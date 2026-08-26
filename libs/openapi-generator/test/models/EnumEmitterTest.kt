package com.strange.openapi.models

import com.strange.openapi.ApiModel
import com.strange.openapi.EnumEntry
import com.strange.openapi.EnumType
import com.strange.openapi.TypeRef
import com.strange.openapi.render
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private val STATUS =
    EnumType(
        name = "Status",
        entries =
            listOf(
                EnumEntry("ACTIVE", "active"),
                EnumEntry("IN_PROGRESS", "in-progress"),
            ),
        base = TypeRef.StringRef,
        fallback = EnumEntry("UNKNOWN", "__unknown__"),
    )

private val CODE =
    EnumType(
        name = "Code",
        entries = listOf(EnumEntry("V400", "400"), EnumEntry("V404", "404")),
        base = TypeRef.IntRef,
        fallback = EnumEntry("UNKNOWN", "-2147483648"),
    )

private fun render(
    enum: EnumType,
    style: ModelStyle = ModelStyle.Kotlinx,
) = ModelsOnlyEmitter(style)
    .render(ApiModel(groups = emptyList(), models = listOf(enum)))
    .getValue("com.example.api.model.${enum.name}")

/**
 * A generated enum has to survive a server that knows more values than the document did. Neither
 * library offers that without the consumer configuring it, so both shapes carry their own
 * mechanism — and the shape underneath has to stay the same either way.
 */
class EnumEmitterTest :
    FeatureSpec({

        feature("the shape both styles share") {
            scenario("entries carry their wire value rather than an annotation naming it") {
                val source = render(STATUS)

                source shouldContain "public enum class Status("
                source shouldContain """ACTIVE("active")"""
                source shouldContain """IN_PROGRESS("in-progress")"""
                // The wire value is a property, so an entry name is free to differ from it —
                // `in-progress` could not be an identifier or a @SerialName-free entry otherwise.
                source shouldNotContain "@SerialName"
            }

            scenario("an unlisted value has an entry, and a wire value no server will take") {
                val source = render(STATUS)

                source shouldContain """UNKNOWN("__unknown__")"""
                source shouldContain "does not list"
            }

            scenario("toString is the wire value, so the enum works outside a JSON body") {
                // A path, query or header parameter is converted with toString by both clients.
                render(STATUS) shouldContain "override fun toString(): String = wireValue"
                render(CODE) shouldContain "override fun toString(): String = wireValue.toString()"
            }

            scenario("the factory falls back rather than throwing") {
                render(STATUS) shouldContain
                    "entries.firstOrNull { it.wireValue == wireValue } ?: UNKNOWN"
            }
        }

        feature("kotlinx binding") {
            scenario("a primitive serializer replaces the one the plugin would generate") {
                val source = render(STATUS, ModelStyle.Kotlinx)

                source shouldContain "@Serializable(with = StatusSerializer::class)"
                source shouldContain "public object StatusSerializer : KSerializer<Status>"
                source shouldContain """PrimitiveSerialDescriptor("Status", PrimitiveKind.STRING)"""
                source shouldContain "encoder.encodeString(value.wireValue)"
                source shouldContain "Status.fromWireValue(decoder.decodeString())"
            }

            scenario("an integer enum encodes as an integer") {
                val source = render(CODE, ModelStyle.Kotlinx)

                source shouldContain "PrimitiveKind.INT"
                source shouldContain "encoder.encodeInt(value.wireValue)"
                source shouldContain "Code.fromWireValue(decoder.decodeInt())"
            }
        }

        feature("jackson binding") {
            scenario("the value getter and a static factory are all Jackson needs") {
                val source = render(STATUS, ModelStyle.Jackson)

                source shouldContain "@get:JsonValue"
                source shouldContain "@JvmStatic"
                source shouldContain "@JsonCreator"
                // No mapper feature, and nothing kotlinx, has to be on the classpath
                source shouldNotContain "@Serializable"
                source shouldNotContain "KSerializer"
            }
        }
    })
