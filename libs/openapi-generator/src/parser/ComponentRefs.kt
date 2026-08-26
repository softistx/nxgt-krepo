package com.strange.openapi.parser

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse

/**
 * Following a `$ref` into `components` for the parts of a document that are not schemas.
 *
 * The document parser resolves schema `$ref`s lazily, because a schema's *name* is what a generated
 * class is called. Nothing is named after a request body or a response, so these are followed
 * eagerly — and they have to be followed at all: a `$ref`'d body carries its content behind the
 * reference, and reading `requestBody.content` straight off the operation finds nothing there.
 * Dropping a body or a return type that way is exactly the silent degradation this generator does
 * not do, so an unresolvable reference fails the parse instead.
 */
internal fun OpenAPI.resolveParameter(
    parameter: Parameter,
    where: String,
): Parameter =
    parameter.`$ref`?.let { ref ->
        components?.parameters?.get(ref.substringAfterLast('/'))
            ?: throw OpenApiParseException("$where: cannot resolve parameter ${'$'}ref '$ref'")
    } ?: parameter

internal fun OpenAPI.resolveRequestBody(
    body: RequestBody,
    where: String,
): RequestBody =
    body.`$ref`?.let { ref ->
        components?.requestBodies?.get(ref.substringAfterLast('/'))
            ?: throw OpenApiParseException("$where: cannot resolve request body ${'$'}ref '$ref'")
    } ?: body

internal fun OpenAPI.resolveResponse(
    response: ApiResponse,
    where: String,
): ApiResponse =
    response.`$ref`?.let { ref ->
        components?.responses?.get(ref.substringAfterLast('/'))
            ?: throw OpenApiParseException("$where: cannot resolve response ${'$'}ref '$ref'")
    } ?: response
