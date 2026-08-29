package com.strange.openapi.emit

import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.Operation

internal const val API_OPERATION: String = "ApiOperation"

/** The [ApiOperation] annotation, by the name it has in the generated package. */
internal fun apiOperationName(options: EmitOptions): ClassName = ClassName(options.utilPackage, API_OPERATION)

/**
 * The annotation that tells the HTTP layer which operation it is looking at.
 *
 * A response arrives at the point where the status code and the body live but the operation is
 * anonymous — and both of the things a client has to do down there, deciding which error type a
 * status maps to and deciding whether to attach a credential, are per-operation facts. Both client
 * styles can read an annotation off the function that made the call: Ktorfit exposes a function's
 * own annotations to a client plugin through `HttpRequest.annotations`, and Spring hands a
 * `HttpRequestValues.Processor` the reflective `Method`. So one annotation carries both facts, and
 * each style reads it its own way.
 *
 * It carries [Operation.id] rather than the error mapping itself. An annotation could hold
 * `Array<KClass<*>>`, but turning a `KClass` back into a deserializer is reflection, and
 * kotlinx.serialization wants a `KSerializer` the generator can write down statically. So the
 * annotation carries a key and the mapping is generated code.
 */
internal fun apiOperationFile(options: EmitOptions): FileSpec {
    val annotation =
        TypeSpec
            .annotationBuilder(API_OPERATION)
            .addKdoc(
                """
                Identifies the operation a request came from, for the generated plugins that run
                below the interface.

                Present on every generated function. Read at runtime, so it is retained at runtime.
                """.trimIndent(),
            ).addModifiers(KModifier.PUBLIC)
            .addAnnotation(
                AnnotationSpec
                    .builder(TARGET)
                    .addMember("%T.FUNCTION", ANNOTATION_TARGET)
                    .build(),
            ).addAnnotation(
                AnnotationSpec
                    .builder(RETENTION)
                    .addMember("%T.RUNTIME", ANNOTATION_RETENTION)
                    .build(),
            ).primaryConstructor(
                FunSpec
                    .constructorBuilder()
                    .addParameter("id", STRING)
                    .addParameter(
                        ParameterSpec
                            .builder("security", ARRAY_OF_STRING)
                            .defaultValue("[]")
                            .build(),
                    ).build(),
            ).addProperty(
                PropertySpec
                    .builder("id", STRING)
                    .initializer("id")
                    .addKdoc("The document's `operationId`.")
                    .build(),
            ).addProperty(
                PropertySpec
                    .builder("security", ARRAY_OF_STRING)
                    .initializer("security")
                    .addKdoc(
                        """
                        The schemes this operation requires, by the document's own name for each.

                        Empty means no credential. The document can say that two ways — by declaring
                        nothing anywhere, or by overriding its root with `security: []` — and to a
                        caller the two are the same instruction.
                        """.trimIndent(),
                    ).build(),
            ).build()

    return FileSpec
        .builder(options.utilPackage, API_OPERATION)
        .addFileComment(GENERATED_COMMENT)
        .addType(annotation)
        .build()
}

internal const val GENERATED_COMMENT: String = "Generated from the OpenAPI document. Do not edit."

private val TARGET = ClassName("kotlin.annotation", "Target")
private val RETENTION = ClassName("kotlin.annotation", "Retention")
private val ANNOTATION_TARGET = ClassName("kotlin.annotation", "AnnotationTarget")
private val ANNOTATION_RETENTION = ClassName("kotlin.annotation", "AnnotationRetention")
private val ARRAY_OF_STRING = ARRAY.parameterizedBy(STRING)
