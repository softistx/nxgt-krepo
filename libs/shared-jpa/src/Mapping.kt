package com.strange.jpa

import com.strange.jpa.json.KotlinxJsonFormatMapper
import jakarta.persistence.Id
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Refuses a `kotlin.uuid.Uuid` identifier, at the one moment it can still be refused.
 *
 * Everywhere else in an entity a Kotlin `Uuid` is fine — `UuidConverter` maps it to Postgres' own
 * `uuid`. An `@Id` is the exception, and not one this module can work around: Hibernate rejects an
 * `AttributeConverter` on an identifier outright (*"'AttributeConverter' not allowed for attribute
 * 'id' annotated '@jakarta.persistence.Id'"*), and the JDBC-bound `UserType` that would otherwise
 * answer it is exactly what Hibernate Reactive's own documentation says not to reach for.
 *
 * What happens without this check is the reason for it. Nothing fails: Hibernate falls back to
 * serializing the unmapped type, the schema comes out with a `bytea` primary key, inserts and reads
 * both work, and the table is unreadable to every other client of that database. It is the same
 * failure `UuidConverter` exists to prevent, in the one place a converter cannot reach.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun rejectUuidIdentifiers(entities: List<KClass<*>>) {
    entities.forEach { entity ->
        generateSequence(entity.java) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { it.isAnnotationPresent(Id::class.java) && it.type == Uuid::class.java }
            .forEach { field ->
                error(
                    "${entity.simpleName}.${field.name} is a kotlin.uuid.Uuid identifier, which cannot be mapped: " +
                        "Hibernate does not allow an AttributeConverter on an @Id, so the column would silently " +
                        "become bytea. Use java.util.UUID for the identifier — kotlin.uuid.Uuid is fine everywhere else.",
                )
            }
    }
}

/**
 * Refuses a JSON column whose type is the wrong shape for the code it was given.
 *
 * Hibernate has two codes: [SqlTypes.JSON] for a document that is an object and [SqlTypes.JSON_ARRAY]
 * for one that is a list. Both produce a `jsonb` column and both round trip perfectly — when they
 * match. Given the wrong one, Hibernate Reactive's binder wraps the rendered document in the Vert.x
 * type for the code it was told, and a `["x","y"]` handed to `JsonObject` fails on the first write
 * with `DecodeException: Failed to decode` and nothing else. The mapping is a startup fact and the
 * failure is a runtime accident, so it is worth turning back into a startup fact.
 *
 * The shape comes from the serializer rather than from the Kotlin type, because the serializer is
 * what decides it: a `@Serializable` class with a custom serializer writing an array is a list here
 * whatever its declaration looks like. A type with no serializer is left alone — that failure has its
 * own message, in [com.strange.jpa.json.KotlinxJsonFormatMapper].
 */
internal fun rejectMismatchedJsonShapes(
    entities: List<KClass<*>>,
    json: Json,
) {
    val mapper = KotlinxJsonFormatMapper(json)
    entities.forEach { entity ->
        generateSequence(entity.java) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .forEach { field ->
                val code = field.getAnnotation(JdbcTypeCode::class.java)?.value ?: return@forEach
                if (code != SqlTypes.JSON && code != SqlTypes.JSON_ARRAY) return@forEach

                val kind = runCatching { mapper.serializerFor(field.genericType).descriptor.kind }.getOrNull()
                val isList = kind == StructureKind.LIST
                if (isList == (code == SqlTypes.JSON_ARRAY)) return@forEach

                val (given, wanted) =
                    if (isList) "SqlTypes.JSON" to "SqlTypes.JSON_ARRAY" else "SqlTypes.JSON_ARRAY" to "SqlTypes.JSON"
                error(
                    "${entity.simpleName}.${field.name} is annotated @JdbcTypeCode($given) and serializes to " +
                        "${if (isList) "an array" else "an object"}: use $wanted. The column would be created and " +
                        "every write to it would fail with a Vert.x \"Failed to decode\".",
                )
            }
    }
}
