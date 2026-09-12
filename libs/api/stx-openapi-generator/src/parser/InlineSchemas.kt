package com.softistx.openapi.parser

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.Operation as SwaggerOperation

/**
 * Promoting inline schemas to named components, before anything else reads the document.
 *
 * A schema written in place rather than behind a `$ref` describes exactly as much as a named one
 * does, but it has no name — and every later stage of this parser is name-driven. Without this pass
 * an inline object degrades to raw JSON and an inline enum degrades to its scalar, which is the
 * single largest source of "the generator ignored my schema" in an ordinary document.
 *
 * The pass rewrites the document rather than the parser: each inline schema that would become a
 * declaration is added to `components.schemas` under a derived name and replaced in place by a
 * `$ref` to it. Everything downstream then sees a document that only ever refs, and needs no
 * knowledge that any of this happened.
 */
internal fun OpenAPI.hoistInlineSchemas() {
    InlineHoist(this).run()
}

/**
 * Names are derived from the path that reaches the schema — `Order` + `shippingAddress` becomes
 * `OrderShippingAddress` — so a name is predictable from the document alone. Array elements take an
 * `Item` suffix and `additionalProperties` values a `Value` suffix rather than a guessed singular:
 * `OrderTagsItem` is ugly where `OrderTag` would be pretty, but singularising `status` gives
 * `Statu`, and a predictable name beats a pretty one that is sometimes wrong.
 */
private class InlineHoist(
    private val openApi: OpenAPI,
) {
    private val hoisted = linkedMapOf<String, Schema<Any>>()

    fun run() {
        openApi.components
            ?.schemas
            .orEmpty()
            .toList()
            .forEach { (name, schema) -> descend(Naming.pascal(name), "schema $name", schema) }
        openApi.paths.orEmpty().forEach { (path, item) ->
            item.readOperationsMap().orEmpty().forEach { (method, operation) ->
                operationSchemas(operation, "$method $path")
            }
        }
    }

    private fun operationSchemas(
        operation: SwaggerOperation,
        where: String,
    ) {
        val base = Naming.pascal(operation.operationId ?: return)

        operation.parameters.orEmpty().forEach { parameter ->
            val name = parameter.name ?: return@forEach
            parameter.schema =
                resolveChild(base + Naming.pascal(name), "$where parameter '$name'", parameter.schema ?: return@forEach)
        }

        jsonBody(operation, where)?.let { media ->
            media.schema = resolveChild("${base}Request", "$where request body", media.schema ?: return@let)
        }
        openApi
            .successResponse(operation, where)
            ?.content
            ?.get("application/json")
            ?.let { media ->
                media.schema = resolveChild("${base}Response", "$where response", media.schema ?: return@let)
            }
    }

    // Only the JSON body: a multipart schema becomes one parameter per part, not a class, so
    // promoting it would emit a declaration nothing ever names.
    private fun jsonBody(
        operation: SwaggerOperation,
        where: String,
    ) = operation.requestBody
        ?.let { openApi.resolveRequestBody(it, where) }
        ?.content
        ?.get("application/json")

    /**
     * Replaces one inline schema with a `$ref`, or leaves it alone when it becomes no declaration.
     *
     * Descent happens first, so an inner name builds on the name its container is about to take and
     * the schema is already in its final form when it is compared for reuse.
     */
    private fun resolveChild(
        candidate: String,
        where: String,
        schema: Schema<Any>,
    ): Schema<Any> {
        if (schema.`$ref` != null) return schema
        descend(candidate, where, schema)
        if (openApi.schemaKindOf(schema) == null) return schema
        return Schema<Any>().apply { `$ref` = "#/components/schemas/${register(candidate, where, schema)}" }
    }

    private fun descend(
        base: String,
        where: String,
        schema: Schema<Any>,
    ) {
        schema.properties?.let { properties ->
            properties.keys.toList().forEach { key ->
                properties[key] =
                    resolveChild(base + Naming.pascal(key), "$where property '$key'", properties.getValue(key))
            }
        }
        @Suppress("UNCHECKED_CAST")
        (schema.items as Schema<Any>?)?.let { schema.items = resolveChild("${base}Item", "$where item", it) }
        @Suppress("UNCHECKED_CAST")
        (schema.additionalProperties as? Schema<Any>)?.let {
            schema.additionalProperties = resolveChild("${base}Value", "$where value", it)
        }
        // Composition branches are descended into but never promoted. An `allOf` branch is merged
        // into its container, and a `oneOf` branch must already be a `$ref` to be a union member at
        // all, so hoisting either would emit a class that no generated signature mentions.
        (schema.allOf.orEmpty() + schema.oneOf.orEmpty() + schema.anyOf.orEmpty())
            .filter { it.`$ref` == null }
            .forEach { descend(base, where, it) }
    }

    /**
     * The derived name, or an already-hoisted one describing exactly the same schema.
     *
     * Reuse matters because the same inline enum is routinely repeated across operations; without
     * it a document with one status enum written out five times generates five identical classes.
     * Reuse is deliberately limited to schemas this pass created: silently retyping a property to a
     * component the document did not point it at would be a different, larger claim.
     */
    private fun register(
        candidate: String,
        where: String,
        schema: Schema<Any>,
    ): String {
        hoisted.entries.firstOrNull { it.value == schema }?.let { return it.key }
        if (openApi.components?.schemas?.containsKey(candidate) == true) {
            throw OpenApiParseException(
                "$where: the inline schema here would be generated as '$candidate', a name the document " +
                    "already defines. Move it into components under a name of its own and ${'$'}ref it.",
            )
        }
        val components = openApi.components ?: Components().also { openApi.components = it }
        components.addSchemas(candidate, schema)
        hoisted[candidate] = schema
        return candidate
    }
}
