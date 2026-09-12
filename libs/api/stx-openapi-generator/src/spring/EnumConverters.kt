package com.softistx.openapi.spring

import com.softistx.openapi.ApiModel
import com.softistx.openapi.EnumType
import com.softistx.openapi.emit.EmitOptions
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.STRING

/**
 * The one piece of wiring a Spring client cannot do for itself.
 *
 * Spring turns a path, query or header argument into a string through its `ConversionService`, and
 * that service converts an enum with `Enum.name()` — never `toString()`. A generated enum's Kotlin
 * name is derived from its wire value and routinely differs from it (`in-progress` becomes
 * `IN_PROGRESS`), so without a converter the client silently sends the wrong value: the request
 * succeeds and comes back empty. Checked against `DefaultFormattingConversionService` rather than
 * assumed, including the documented "enum implements an interface" escape, which does not apply.
 *
 * The registration function is generated so the list stays in step with the document; the one line
 * that hands it to the proxy factory belongs to the consumer, who owns the factory.
 */
internal fun enumConverterFile(
    model: ApiModel,
    options: EmitOptions,
): FileSpec? {
    val enums = model.models.filterIsInstance<EnumType>().sortedBy { it.name }
    if (enums.isEmpty()) return null

    val register =
        FunSpec
            .builder("registerApiEnumConverters")
            .addModifiers(KModifier.PUBLIC)
            .addKdoc(
                """
                Teaches a Spring `ConversionService` to write every generated enum as its wire value.

                Spring converts an enum argument with `Enum.name()`, which is the Kotlin name rather
                than the value the document lists, so a client that skips this sends the wrong string
                for any enum whose two differ. Hand the service to the factory that builds the client:

                ```kotlin
                val conversions = DefaultFormattingConversionService().also(::registerApiEnumConverters)
                HttpServiceProxyFactory.builderFor(adapter).conversionService(conversions).build()
                ```
                """.trimIndent(),
            ).addParameter("registry", CONVERTER_REGISTRY)
            .apply {
                enums.forEach { enum ->
                    // toString() is the wire value for a string enum and its decimal form for an
                    // integer one, so one statement covers both bases.
                    addStatement(
                        "registry.addConverter(%T::class.java, %T::class.java) { it.toString() }",
                        ClassName(options.modelPackage, enum.name),
                        STRING,
                    )
                }
            }.build()

    return FileSpec
        .builder(options.utilPackage, "ApiEnumConverters")
        .addFileComment("Generated from the OpenAPI document. Do not edit.")
        .addFunction(register)
        .build()
}

private val CONVERTER_REGISTRY = ClassName("org.springframework.core.convert.converter", "ConverterRegistry")
