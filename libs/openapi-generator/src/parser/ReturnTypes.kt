package com.strange.openapi.parser

import com.strange.openapi.TypeRef
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.Operation as SwaggerOperation

/**
 * The response a return type is read from: the lowest 2xx the operation declares.
 *
 * Not whichever the document happened to list first — an operation declaring both 200 and 201
 * should always generate the same return type. [hoistInlineSchemas] picks the same response, so an
 * inline body is promoted exactly where one is read.
 */
internal fun OpenAPI.successResponse(
    operation: SwaggerOperation,
    where: String,
): ApiResponse? =
    operation.responses
        ?.entries
        ?.filter { (code, _) -> code.startsWith("2") }
        ?.minByOrNull { (code, _) -> code }
        ?.let { (code, response) -> resolveResponse(response, "$where response $code") }

/** The type an operation's function returns, read from its success response. */
internal fun OpenAPI.parseReturnType(operation: SwaggerOperation): TypeRef {
    val content = successResponse(operation, operation.operationId.orEmpty())?.content ?: return TypeRef.UnitRef
    content["application/json"]?.schema?.let { return typeOf(it, "${operation.operationId} response") }
    if (content.keys.any { it.startsWith("text/") }) return TypeRef.StringRef
    return TypeRef.UnitRef
}
