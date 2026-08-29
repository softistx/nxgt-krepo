package com.strange.openapi.emit

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STRING
import com.strange.openapi.ApiModel
import com.strange.openapi.Operation
import com.strange.openapi.TypeRef

internal const val API_ERROR_OF: String = "apiErrorOf"

/** The name of the per-schema parse helper for [schema] — `ErrorResponse` -> `errorResponseError`. */
internal fun parserNameFor(schema: String): String = schema.replaceFirstChar { it.lowercase() } + "Error"

/**
 * `apiErrorOf(operationId, status, rawBody)`: which exception a failed call becomes.
 *
 * Written as a `when` over the operation and then the status, rather than a map built at runtime,
 * because the leaf of every branch has to name a deserializer — a `KSerializer` for kotlinx, a
 * class literal for Jackson — and naming it statically is what keeps reflection out of the error
 * path. It is also what makes the mapping readable: the generated file *is* the document's table
 * of failures.
 *
 * Both client styles share this shape and differ only in the per-schema helpers it calls, which
 * each style writes for its own deserializer. That is the whole difference between them here.
 */
internal fun apiErrorDispatch(
    model: ApiModel,
    options: EmitOptions,
    decoderParameters: List<ParameterSpec>,
    decoderArguments: String,
): FunSpec {
    val operations =
        model.groups
            .asSequence()
            .flatMap { it.operations }
            .filter { it.typedErrors().isNotEmpty() }
            .sortedBy { it.id }
            .toList()

    val body =
        CodeBlock
            .builder()
            .beginControlFlow("return when (operationId)")
            .apply {
                operations.forEach { operation ->
                    beginControlFlow("%S -> when (status)", operation.id)
                    operation.byStatus().forEach { (schema, codes) ->
                        addStatement(
                            "%L -> %L(%L)",
                            codes.joinToString(", "),
                            parserNameFor(schema),
                            decoderArguments,
                        )
                    }
                    // The document's `default` response is the client's `else`: it is what the
                    // document says about a status it did not enumerate.
                    addStatement(
                        "else -> %L",
                        operation.defaultSchema()?.let { "${parserNameFor(it)}($decoderArguments)" }
                            ?: "$API_EXCEPTION(status, rawBody)",
                    )
                    endControlFlow()
                }
                addStatement("else -> %L(status, rawBody)", API_EXCEPTION)
            }.endControlFlow()
            .build()

    return FunSpec
        .builder(API_ERROR_OF)
        .addModifiers(KModifier.INTERNAL)
        .addKdoc(
            """
            The exception a failed call becomes, from the operation that made it.

            An operation the document says nothing about, a status it did not declare, and a body
            that does not parse all end at [$API_EXCEPTION] with the body attached. An error path
            that itself failed opaquely would be worse than the untyped failure it replaced.
            """.trimIndent(),
        ).addParameters(decoderParameters)
        .addParameter("operationId", STRING.copy(nullable = true))
        .addParameter("status", INT)
        .addParameter("rawBody", STRING)
        .returns(apiExceptionName(options))
        .addCode(body)
        .build()
}

/**
 * The statuses this operation declares, grouped by the schema they carry, in status order.
 *
 * Grouped rather than listed one status at a time because a real document reuses one error schema
 * across every failure — all 169 of the ones in `examples/demo-api/openapi.yaml` are the same shape —
 * and a branch per status would be a hundred lines saying the same thing.
 */
private fun Operation.byStatus(): Map<String, List<Int>> =
    typedErrors()
        .mapNotNull { error -> error.code?.let { (error.type as TypeRef.ModelRef).name to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, codes) -> codes.sorted() }

/** The schema of the document's `default` response, if it declares one with a generated body. */
private fun Operation.defaultSchema(): String? =
    errors
        .firstOrNull { it.code == null }
        ?.let { (it.type as? TypeRef.ModelRef)?.name }

private fun Operation.typedErrors() = errors.filter { it.type is TypeRef.ModelRef }
