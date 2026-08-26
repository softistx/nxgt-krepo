package com.strange.openapi.parser

import com.strange.openapi.Field
import com.strange.openapi.ModelType
import com.strange.openapi.TypeRef
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation as SwaggerOperation

/**
 * Turning the document's component schemas into [ModelType]s, and an operation's success
 * response into the type its function returns.
 */
internal fun OpenAPI.parseModels(): List<ModelType> =
    components
        ?.schemas
        .orEmpty()
        .mapNotNull { (name, schema) ->
            val properties = schema.properties.orEmpty()
            // A schema with no properties has no class worth generating; it is carried as raw JSON.
            if (properties.isEmpty()) return@mapNotNull null
            val required = schema.required.orEmpty().toSet()
            ModelType(
                name = Naming.pascal(name),
                fields =
                    properties.map { (propertyName, propertySchema) ->
                        Field(
                            name = Naming.propertyName(propertyName),
                            wireName = propertyName,
                            type = typeOf(propertySchema, "model $name property '$propertyName'"),
                            required = propertyName in required,
                        )
                    },
            )
        }.sortedBy { it.name }

internal fun OpenAPI.parseReturnType(operation: SwaggerOperation): TypeRef {
    val success =
        operation.responses
            ?.entries
            ?.firstOrNull { (code, _) -> code.startsWith("2") }
            ?.value
            ?: return TypeRef.UnitRef
    val content = success.content ?: return TypeRef.UnitRef
    content["application/json"]?.schema?.let { return typeOf(it, "${operation.operationId} response") }
    if (content.keys.any { it.startsWith("text/") }) return TypeRef.StringRef
    return TypeRef.UnitRef
}
