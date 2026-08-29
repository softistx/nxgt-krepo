package com.strange.openapi.models

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec

/**
 * Jackson binds the enum through the pair it already understands: a value getter and a factory.
 *
 * `@JsonEnumDefaultValue` would be the obvious route and is unusable here — it only fires when the
 * consumer sets `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` on their own `ObjectMapper`. A
 * `@JsonCreator` factory needs nothing enabled, and `@JvmStatic` puts it where Jackson looks: the
 * annotation survives onto the static bridge Kotlin generates for it.
 */
internal object JacksonEnumBinding : EnumBinding {
    override fun decorateWireValue(builder: PropertySpec.Builder) {
        // On the getter, not the field: @JsonValue has no PARAMETER target.
        builder.addAnnotation(
            AnnotationSpec
                .builder(JSON_VALUE)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                .build(),
        )
    }

    override fun decorateFactory(builder: FunSpec.Builder) {
        builder
            .addAnnotation(ClassName("kotlin.jvm", "JvmStatic"))
            .addAnnotation(JSON_CREATOR)
    }
}
