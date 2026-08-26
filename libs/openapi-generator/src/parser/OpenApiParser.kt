package com.strange.openapi.parser

import com.strange.openapi.ApiGroup
import com.strange.openapi.ApiModel
import com.strange.openapi.Operation
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Reads an OpenAPI document into an [ApiModel].
 *
 * `$ref`s are deliberately *not* inlined: resolving fully would erase the component names that
 * generated model classes are named after.
 *
 * Inline schemas are promoted to named components first (`InlineSchemas.kt`), so the rest of the
 * parser only ever resolves names.
 *
 * The parser knows nothing about any client style. Schema-to-type resolution lives in
 * `SchemaTypes.kt`, parameters in `ParameterParsing.kt`, and models in `ModelParsing.kt`.
 */
public class OpenApiParser(
    private val grouping: Grouping = Grouping.Tag,
    private val naming: InterfaceNaming = InterfaceNaming(),
) {
    public fun parse(specFile: Path): ApiModel = parse(specFile.readText(), specFile.toString())

    public fun parse(
        spec: String,
        source: String = "<spec>",
    ): ApiModel {
        val result = OpenAPIParser().readContents(spec, null, ParseOptions().apply { isResolve = true })
        val openApi =
            result.openAPI
                ?: throw OpenApiParseException(
                    "could not parse $source: ${result.messages?.joinToString("; ").orEmpty()}",
                )
        // Before anything reads the document: an inline schema that would become a declaration is
        // given a name and a place in `components`, so every later stage only ever sees a `$ref`.
        openApi.requireKnownKotlinExtensions()
        openApi.hoistInlineSchemas()
        return ApiModel(groups = parseGroups(openApi), models = openApi.parseModels())
    }

    private fun parseGroups(openApi: OpenAPI): List<ApiGroup> {
        val byGroup = linkedMapOf<String, MutableList<Operation>>()
        openApi.paths.orEmpty().forEach { (path, item) ->
            item.readOperationsMap().forEach { (method, operation) ->
                val id =
                    operation.operationId
                        ?: throw OpenApiParseException(
                            "$method $path has no operationId; cannot name a function for it",
                        )
                byGroup.getOrPut(groupKeyOf(operation, path)) { mutableListOf() } +=
                    Operation(
                        name = operation.extensions.kotlinName("$method $path") ?: Naming.functionName(id),
                        httpMethod = method.name,
                        path = path.trimStart('/'),
                        parameters = openApi.parseParameters(operation, "$method $path"),
                        returnType = openApi.parseReturnType(operation),
                        summary = operation.summary,
                        deprecated = operation.deprecated == true,
                        deprecatedReason = operation.extensions.deprecatedReason("$method $path"),
                    )
            }
        }
        return byGroup
            .map { (tag, ops) -> ApiGroup(interfaceNameOf(openApi, tag), ops.sortedBy { it.name }) }
            .mergeSameNamedGroups()
    }

    /**
     * A tag's `x-kotlin-name` names the interface outright: prefix and suffix are this generator's
     * derivation, and a document that states the name is not asking for it to be derived.
     */
    private fun interfaceNameOf(
        openApi: OpenAPI,
        tag: String,
    ): String =
        openApi.tags
            .orEmpty()
            .firstOrNull { it.name == tag }
            ?.extensions
            .kotlinName("tag '$tag'")
            ?: Naming.interfaceName(tag, naming)

    private fun groupKeyOf(
        operation: io.swagger.v3.oas.models.Operation,
        path: String,
    ): String =
        when (grouping) {
            Grouping.Tag -> operation.tags?.firstOrNull() ?: "default"
            Grouping.Path -> path.trim('/').substringBefore('/').ifEmpty { "default" }
            Grouping.None -> "default"
        }
}
