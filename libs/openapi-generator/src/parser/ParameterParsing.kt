package com.strange.openapi.parser

import com.strange.openapi.Param
import com.strange.openapi.ParamKind
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.Operation as SwaggerOperation

/**
 * Turning an operation's parameters and request body into [Param]s.
 *
 * Nothing here is ever skipped quietly: a parameter location or a request media type this
 * generator cannot represent fails the parse, naming the operation and the reason.
 */
internal fun OpenAPI.parseParameters(
    operation: SwaggerOperation,
    where: String,
): List<Param> = namedParameters(operation, where) + bodyParameters(operation, where)

private fun OpenAPI.namedParameters(
    operation: SwaggerOperation,
    where: String,
): List<Param> =
    operation.parameters.orEmpty().map { raw ->
        val parameter = resolveParameter(raw, where)
        val kind =
            when (parameter.`in`) {
                "path" -> ParamKind.Path

                "query" -> ParamKind.Query

                "header" -> ParamKind.Header

                else -> throw OpenApiParseException(
                    "$where: parameter '${parameter.name}' is in '${parameter.`in`}', which is not supported",
                )
            }
        Param(
            name = Naming.propertyName(parameter.name),
            wireName = parameter.name,
            kind = kind,
            type = typeOf(parameter.schema, "$where parameter '${parameter.name}'"),
            // A path parameter is required whether or not the document bothers to say so.
            required = parameter.required == true || kind == ParamKind.Path,
            nullable = parameter.schema?.isNullable() == true,
            default = parameter.schema?.defaultLiteral(),
        )
    }

private fun OpenAPI.bodyParameters(
    operation: SwaggerOperation,
    where: String,
): List<Param> {
    val body = operation.requestBody ?: return emptyList()
    val content = body.content ?: return emptyList()

    content["application/json"]?.schema?.let { schema ->
        return listOf(
            Param(
                name = "body",
                wireName = "body",
                kind = ParamKind.Body,
                type = typeOf(schema, "$where request body"),
                required = body.required != false,
                nullable = schema.isNullable(),
            ),
        )
    }
    content["multipart/form-data"]?.schema?.let { return multipartParts(it, where) }

    throw OpenApiParseException(
        "$where: request body media types ${content.keys} are not supported " +
            "(expected application/json or multipart/form-data)",
    )
}

private fun OpenAPI.multipartParts(
    rawSchema: io.swagger.v3.oas.models.media.Schema<*>,
    where: String,
): List<Param> {
    // The multipart schema is often a $ref to a component; its parts live there.
    val schema = resolveSchema(rawSchema, where)
    val properties = schema.properties.orEmpty()
    if (properties.isEmpty()) {
        throw OpenApiParseException("$where: multipart body has no declared properties")
    }
    val requiredNames = schema.required.orEmpty().toSet()
    return properties.map { (name, propertySchema) ->
        Param(
            name = Naming.propertyName(name),
            wireName = name,
            kind = ParamKind.Part,
            type = typeOf(propertySchema, "$where part '$name'"),
            required = name in requiredNames,
            nullable = propertySchema.isNullable(),
            default = propertySchema.defaultLiteral(),
        )
    }
}

private fun OpenAPI.resolveParameter(
    parameter: Parameter,
    where: String,
): Parameter {
    val ref = parameter.`$ref` ?: return parameter
    val name = ref.substringAfterLast('/')
    return components?.parameters?.get(name)
        ?: throw OpenApiParseException("$where: cannot resolve parameter ${'$'}ref '$ref'")
}
