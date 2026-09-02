package com.softistx.jpa.json

import com.softistx.common.concurrent.Memo
import com.softistx.common.serialization.decodeValue
import com.softistx.jpa.JpaDocumentException
import com.softistx.jpa.JpaSerializerException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull
import org.hibernate.type.format.AbstractJsonFormatMapper
import java.lang.reflect.Type

/**
 * Hibernate's JSON `FormatMapper`, implemented over kotlinx.serialization.
 *
 * Hibernate finds one of these by looking for Jackson, then Jackson 3, then JSON-B, and throws when
 * it finds none — telling a Kotlin codebase to put Jackson on its classpath. This is that hook,
 * filled with the serializer every class in this repo already carries, which is the whole of what
 * "built-in" means here.
 *
 * [AbstractJsonFormatMapper] reduces the SPI to two methods over a `java.lang.reflect.Type`, and
 * kotlinx answers exactly that shape — so a generic attribute is resolved from the reflective type
 * Hibernate hands over and nothing here needs to know an attribute's type at compile time.
 *
 * **Null never reaches this.** Hibernate's binder short-circuits a null before it binds and
 * `JsonJdbcType` returns early on a null column, so a nullable attribute is safe even though the
 * serializer resolved here is not a nullable one.
 *
 * **An interface-typed attribute fails later, not here.** kotlinx answers an interface with a
 * polymorphic serializer rather than nothing, so the failure arrives at the first write as "not
 * registered for polymorphic serialization" — which is the accurate message for it.
 */
internal class KotlinxJsonFormatMapper(
    private val json: Json,
) : AbstractJsonFormatMapper() {
    /**
     * Resolved serializers, kept because resolving one is reflective.
     *
     * `serializerOrNull(Type)` looks up a `serializer()` method by reflection and invokes it on every
     * call, and this is called once per JSON column per row. A [Memo] rather than one of
     * `stx-common`'s coroutine types because Hibernate calls this from a binder that cannot suspend
     * — and rather than the `ConcurrentHashMap` plus `getOrPut` this used to be, which let two
     * threads resolve the same type at once. `Memo` has that argument at length.
     */
    private val serializers =
        Memo<Type, KSerializer<Any>> { type ->
            json.serializersModule.serializerOrNull(type) ?: throw JpaSerializerException(type)
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T> toString(
        value: T,
        type: Type,
    ): String = json.encodeToString(serializerFor(type), value as Any)

    @Suppress("UNCHECKED_CAST")
    override fun <T> fromString(
        charSequence: CharSequence,
        type: Type,
    ): T = decode(charSequence, type) as T

    /**
     * The read, without the SPI's `protected` in front of it.
     *
     * Internal for the same reason [serializerFor] is: the spec that pins [JpaDocumentException] —
     * the branch that fires on a document an older version of the class wrote — needs no database
     * and should not have to start one to ask this question.
     */
    internal fun decode(
        charSequence: CharSequence,
        type: Type,
    ): Any =
        json.decodeValue(serializerFor(type), charSequence.toString()) {
            JpaDocumentException(type, it)
        }

    /**
     * `serializerOrNull` rather than `serializer`, so a type with no `@Serializable` fails naming the
     * column mapping that wanted it. kotlinx's own message — "Serializer for class 'X' is not found"
     * — is accurate and says nothing about *why* the class is being serialized, which from inside a
     * Hibernate binder is the half that is missing.
     *
     * Internal rather than private because the spec that pins that message needs no database and
     * should not have to start one to ask this question.
     */
    internal fun serializerFor(type: Type): KSerializer<Any> = serializers[type]
}
