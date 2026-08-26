package com.strange.openapi.parser

import com.strange.openapi.ErrorResponse
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation as SwaggerOperation

/** The status the document uses for "anything else". */
internal const val DEFAULT_STATUS: String = "default"

/**
 * The responses an operation declares that a caller does not want.
 *
 * The mirror of [parseReturnType]: that reads the lowest 2xx, this reads everything else. A `1xx`
 * or `3xx` is included too — the generated client is not the thing that follows a redirect, so a
 * declared `304` is a documented outcome like any other.
 *
 * Responses arrive through [resolveResponse] because in a real document they are almost never
 * inline: `apps/demo-api/openapi.yaml` declares 169 of them and every one is a `$ref` into
 * `components/responses`, whose content lives behind the reference.
 */
internal fun OpenAPI.parseErrorResponses(operation: SwaggerOperation): List<ErrorResponse> =
    operation.responses
        .orEmpty()
        .entries
        .filterNot { (code, _) -> code.startsWith("2") }
        .map { (code, declared) ->
            val where = "${operation.operationId.orEmpty()} response $code"
            val response = resolveResponse(declared, where)
            ErrorResponse(
                status = code,
                type =
                    response.content
                        ?.get("application/json")
                        ?.schema
                        ?.let { typeOf(it, where) },
                description = response.description,
            )
        }.sortedBy { it.code ?: Int.MAX_VALUE }
