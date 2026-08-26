package com.strange.openapi.models

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
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
                    // kotlinx has no date type of its own, so this one needs kotlinx-datetime on
                    // the consuming module's classpath — see the generator README.
                    localDate = ClassName("kotlinx.datetime", "LocalDate"),
                    uuid = ClassName("kotlin.uuid", "Uuid"),
                    freeForm = ClassName("kotlinx.serialization.json", "JsonObject"),
                )
            }

            // Jackson's JSR-310 module knows java.time.Instant, not kotlin.time.Instant, and binds a
            // free-form object to a plain Map — so no Jackson type leaks into the generated model.
            ModelStyle.Jackson -> {
                TypeStyle(
                    instant = ClassName("java.time", "Instant"),
                    localDate = ClassName("java.time", "LocalDate"),
                    uuid = ClassName("java.util", "UUID"),
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
 * Anything the file itself needs before its declarations.
 *
 * kotlinx marks both `@JsonIgnoreUnknownKeys` and `@EncodeDefault` experimental, and a generated
 * file uses one or both — so the opt-in is the file's business rather than any one declaration's.
 */
internal fun ModelStyle.decorateFile(builder: FileSpec.Builder) {
    if (this != ModelStyle.Kotlinx) return
    builder.addAnnotation(
        AnnotationSpec
            .builder(ClassName("kotlin", "OptIn"))
            .addMember("%T::class", EXPERIMENTAL_SERIALIZATION_API)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
            .build(),
    )
}

/**
 * Applied to every generated model, so a server that adds a field does not break an older client.
 *
 * Both libraries would otherwise be strict by default — kotlinx always, Jackson whenever the
 * consumer turns `FAIL_ON_UNKNOWN_PROPERTIES` on — and neither switch is ours to set. The same
 * reasoning as a tolerant enum: reading is where a client should bend.
 */
internal val ModelStyle.unknownFieldTolerance: AnnotationSpec
    get() =
        when (this) {
            ModelStyle.Kotlinx -> {
                AnnotationSpec.builder(JSON_IGNORE_UNKNOWN_KEYS).build()
            }

            ModelStyle.Jackson -> {
                AnnotationSpec.builder(JSON_IGNORE_PROPERTIES).addMember("ignoreUnknown = true").build()
            }
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
