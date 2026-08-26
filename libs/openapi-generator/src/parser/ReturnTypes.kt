package com.strange.openapi.parser

import com.strange.openapi.TypeRef
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation as SwaggerOperation

/** The type an operation's function returns, read from its success response. */
internal fun OpenAPI.parseReturnType(operation: SwaggerOperation): TypeRef {
    // The lowest 2xx, not whichever the document happened to list first: an operation declaring
    // both 200 and 201 should always generate the same return type.
    val success =
        operation.responses
            ?.entries
            ?.filter { (code, _) -> code.startsWith("2") }
            ?.minByOrNull { (code, _) -> code }
            ?.value
            ?: return TypeRef.UnitRef
    val content = success.content ?: return TypeRef.UnitRef
    content["application/json"]?.schema?.let { return typeOf(it, "${operation.operationId} response") }
    if (content.keys.any { it.startsWith("text/") }) return TypeRef.StringRef
    return TypeRef.UnitRef
}
