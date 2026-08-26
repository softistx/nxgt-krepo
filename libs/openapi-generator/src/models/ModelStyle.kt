package com.strange.openapi.models

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.strange.openapi.emit.TypeStyle

/**
 * Which serialization library the generated models target.
 *
 * It decides more than annotations: the two spec types with no single Kotlin equivalent —
 * `date-time` and a free-form object — map to whatever that library can bind, and the client
 * interfaces use the same mapping so their signatures line up with their models.
 */
public enum class ModelStyle {
    /** kotlinx.serialization: `@Serializable`, `kotlin.time.Instant`, `JsonObject`. */
    Kotlinx,

    /** Jackson: no class annotation needed, `java.time.Instant`, a plain `Map`. */
    Jackson,
}

public val ModelStyle.types: TypeStyle
    get() =
        when (this) {
            // kotlin.time.Instant, not kotlinx.datetime.Instant: the latter is a deprecated typealias
            // for it, and the stdlib type needs no dependency in the consuming module.
            ModelStyle.Kotlinx -> {
                TypeStyle(
                    instant = ClassName("kotlin.time", "Instant"),
                    freeForm = ClassName("kotlinx.serialization.json", "JsonObject"),
                )
            }

            // Jackson's JSR-310 module knows java.time.Instant, not kotlin.time.Instant, and binds a
            // free-form object to a plain Map — so no Jackson type leaks into the generated model.
            ModelStyle.Jackson -> {
                TypeStyle(
                    instant = ClassName("java.time", "Instant"),
                    freeForm = MAP.parameterizedBy(STRING, ANY.copy(nullable = true)),
                )
            }
        }

/** Applied to the data class itself. Jackson needs nothing; kotlinx needs `@Serializable`. */
internal val ModelStyle.classAnnotations: List<AnnotationSpec>
    get() =
        when (this) {
            ModelStyle.Kotlinx -> listOf(AnnotationSpec.builder(SERIALIZABLE).build())
            ModelStyle.Jackson -> emptyList()
        }

/**
 * Applied to a property whose wire name differs from its Kotlin name — and only then, which keeps
 * the annotation artifact off the classpath of a module whose spec never needs it.
 */
internal fun ModelStyle.wireNameAnnotation(wireName: String): AnnotationSpec =
    when (this) {
        ModelStyle.Kotlinx -> AnnotationSpec.builder(SERIAL_NAME).addMember("%S", wireName).build()
        ModelStyle.Jackson -> AnnotationSpec.builder(JSON_PROPERTY).addMember("%S", wireName).build()
    }

private val SERIALIZABLE = ClassName("kotlinx.serialization", "Serializable")
private val SERIAL_NAME = ClassName("kotlinx.serialization", "SerialName")

// Jackson 3 moved its databind packages, but the annotations stayed at com.fasterxml.
private val JSON_PROPERTY = ClassName("com.fasterxml.jackson.annotation", "JsonProperty")
