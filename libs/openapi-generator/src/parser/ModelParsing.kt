package com.strange.openapi.parser

import com.strange.openapi.Field
import com.strange.openapi.ModelType
import com.strange.openapi.ObjectType
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
            // A schema that becomes no declaration is carried as its underlying type instead.
            if (!schema.isModelled()) return@mapNotNull null
            if (schema.hasGeneratableEnum()) {
                return@mapNotNull enumTypeOf(schema, Naming.pascal(name), "model $name")
            }
            val properties = schema.properties.orEmpty()
            val required = schema.required.orEmpty().toSet()
            ObjectType(
                name = Naming.pascal(name),
                fields =
                    properties.map { (propertyName, propertySchema) ->
                        Field(
                            name = Naming.propertyName(propertyName),
                            wireName = propertyName,
                            type = typeOf(propertySchema, "model $name property '$propertyName'"),
                            required = propertyName in required,
                            nullable = propertySchema.isNullable(),
                            default = propertySchema.defaultLiteral(),
                        )
                    },
            )
        }.sortedBy { it.name }
        .requireDistinctNames()

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
