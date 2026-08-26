package dev.nxgt.openapi

import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.parser.core.models.ParseOptions
import java.nio.file.Path
import kotlin.io.path.readText

/** Raised for a spec this generator cannot faithfully represent. Never skip silently. */
public class OpenApiParseException(message: String) : IllegalArgumentException(message)

/**
 * Reads an OpenAPI document into a [ClientModel].
 *
 * `$ref`s are deliberately *not* inlined: resolving fully would erase the component
 * names that generated model classes are named after.
 */
public class OpenApiParser(private val grouping: Grouping = Grouping.Tag) {

    public fun parse(specFile: Path): ClientModel = parse(specFile.readText(), specFile.toString())

    public fun parse(spec: String, source: String = "<spec>"): ClientModel {
        val result = OpenAPIParser().readContents(spec, null, ParseOptions().apply { isResolve = true })
        val openApi = result.openAPI
            ?: throw OpenApiParseException(
                "could not parse $source: ${result.messages?.joinToString("; ").orEmpty()}"
            )
        return ClientModel(groups = parseGroups(openApi), models = parseModels(openApi))
    }

    private fun parseGroups(openApi: OpenAPI): List<ApiGroup> {
        val byGroup = linkedMapOf<String, MutableList<Operation>>()
        openApi.paths.orEmpty().forEach { (path, item) ->
            item.readOperationsMap().forEach { (method, operation) ->
                val id = operation.operationId
                    ?: throw OpenApiParseException("$method $path has no operationId; cannot name a function for it")
                val key = when (grouping) {
                    Grouping.Tag -> operation.tags?.firstOrNull() ?: "default"
                    Grouping.Path -> path.trim('/').substringBefore('/').ifEmpty { "default" }
                    Grouping.None -> "default"
                }
                byGroup.getOrPut(key) { mutableListOf() } += Operation(
                    name = Naming.functionName(id),
                    httpMethod = method.name,
                    path = path.trimStart('/'),
                    parameters = parseParameters(openApi, operation, "$method $path"),
                    returnType = parseReturnType(operation),
                    summary = operation.summary,
                )
            }
        }
        return byGroup.map { (tag, ops) -> ApiGroup(Naming.interfaceName(tag), ops.sortedBy { it.name }) }
            .sortedBy { it.name }
    }

    private fun parseParameters(
        openApi: OpenAPI,
        operation: io.swagger.v3.oas.models.Operation,
        where: String,
    ): List<Param> {
        val params = operation.parameters.orEmpty().map { raw ->
            val parameter = resolveParameter(openApi, raw, where)
            val kind = when (parameter.`in`) {
                "path" -> ParamKind.Path
                "query" -> ParamKind.Query
                "header" -> ParamKind.Header
                else -> throw OpenApiParseException(
                    "$where: parameter '${parameter.name}' is in '${parameter.`in`}', which is not supported"
                )
            }
            Param(
                name = Naming.propertyName(parameter.name),
                wireName = parameter.name,
                kind = kind,
                type = typeOf(parameter.schema, "$where parameter '${parameter.name}'"),
                required = parameter.required == true || kind == ParamKind.Path,
            )
        }

        val body = operation.requestBody ?: return params
        val content = body.content ?: return params
        val required = body.required != false

        content["application/json"]?.schema?.let { schema ->
            return params + Param(
                name = "body",
                wireName = "body",
                kind = ParamKind.Body,
                type = typeOf(schema, "$where request body"),
                required = required,
            )
        }
        content["multipart/form-data"]?.schema?.let { rawSchema ->
            // The multipart schema is often a $ref to a component; its parts live there.
            val schema = resolveSchema(openApi, rawSchema, where)
            val properties = schema.properties.orEmpty()
            if (properties.isEmpty()) {
                throw OpenApiParseException("$where: multipart body has no declared properties")
            }
            val requiredNames = schema.required.orEmpty().toSet()
            return params + properties.map { (name, propertySchema) ->
                Param(
                    name = Naming.propertyName(name),
                    wireName = name,
                    kind = ParamKind.Part,
                    type = typeOf(propertySchema, "$where part '$name'"),
                    required = name in requiredNames,
                )
            }
        }
        throw OpenApiParseException(
            "$where: request body media types ${content.keys} are not supported " +
                "(expected application/json or multipart/form-data)"
        )
    }

    /** Follows a component `$ref` one level, for places where the parts matter more than the name. */
    private fun resolveSchema(openApi: OpenAPI, schema: Schema<*>, where: String): Schema<*> {
        val ref = schema.`$ref` ?: return schema
        val name = ref.substringAfterLast('/')
        return openApi.components?.schemas?.get(name)
            ?: throw OpenApiParseException("$where: cannot resolve schema ${'$'}ref '$ref'")
    }

    private fun resolveParameter(openApi: OpenAPI, parameter: Parameter, where: String): Parameter {
        val ref = parameter.`$ref` ?: return parameter
        val name = ref.substringAfterLast('/')
        return openApi.components?.parameters?.get(name)
            ?: throw OpenApiParseException("$where: cannot resolve parameter \$ref '$ref'")
    }

    private fun parseReturnType(operation: io.swagger.v3.oas.models.Operation): TypeRef {
        val success = operation.responses?.entries
            ?.firstOrNull { (code, _) -> code.startsWith("2") }
            ?.value
            ?: return TypeRef.UnitRef
        val content = success.content ?: return TypeRef.UnitRef
        content["application/json"]?.schema?.let { return typeOf(it, "${operation.operationId} response") }
        if (content.keys.any { it.startsWith("text/") }) return TypeRef.StringRef
        return TypeRef.UnitRef
    }

    private fun typeOf(schema: Schema<*>?, where: String): TypeRef {
        if (schema == null) return TypeRef.JsonObjectRef
        schema.`$ref`?.let { return TypeRef.ModelRef(Naming.pascal(it.substringAfterLast('/'))) }

        // OpenAPI 3.1 allows `type` to be a set; 3.0 uses a single value.
        val type = schema.type ?: schema.types?.firstOrNull()
        return when (type) {
            "array" -> TypeRef.ListRef(typeOf(schema.items, "$where item"))
            "string" -> when (schema.format) {
                "date-time" -> TypeRef.InstantRef
                "binary" -> TypeRef.BinaryRef
                else -> TypeRef.StringRef
            }
            "integer" -> if (schema.format == "int64") TypeRef.LongRef else TypeRef.IntRef
            "number" -> TypeRef.DoubleRef
            "boolean" -> TypeRef.BooleanRef
            "object", null -> TypeRef.JsonObjectRef
            else -> throw OpenApiParseException("$where: unsupported schema type '$type'")
        }
    }

    private fun parseModels(openApi: OpenAPI): List<ModelType> {
        val schemas = openApi.components?.schemas.orEmpty()
        return schemas.mapNotNull { (name, schema) ->
            val properties = schema.properties.orEmpty()
            if (properties.isEmpty()) return@mapNotNull null // carried as raw JSON instead
            val required = schema.required.orEmpty().toSet()
            ModelType(
                name = Naming.pascal(name),
                fields = properties.map { (propertyName, propertySchema) ->
                    Field(
                        name = Naming.propertyName(propertyName),
                        wireName = propertyName,
                        type = typeOf(propertySchema, "model $name property '$propertyName'"),
                        required = propertyName in required,
                    )
                },
            )
        }.sortedBy { it.name }
    }
}
