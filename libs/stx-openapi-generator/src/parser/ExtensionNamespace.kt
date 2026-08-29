package com.strange.openapi.parser

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Rejects an `x-kotlin-*` key this generator does not implement.
 *
 * Extensions from other toolchains — `x-amazon-apigateway-*`, `x-codegen-*`, `x-readme-*` — are
 * ignored in silence, because they are not this generator's vocabulary and a document is entitled
 * to carry them. `x-kotlin-*` is ours, so a key in it that means nothing here is a typo, and a
 * setting that quietly never applies is exactly the failure this generator refuses elsewhere.
 *
 * The whole document is walked, not only the parts that happen to be read, so a misspelling is
 * caught wherever it sits rather than only where a feature would have looked for it.
 */
internal fun OpenAPI.requireKnownKotlinExtensions() {
    val walk = NamespaceWalk()
    walk.check(extensions, "the document")
    tags.orEmpty().forEach { walk.check(it.extensions, "tag '${it.name}'") }
    components?.schemas.orEmpty().forEach { (name, schema) -> walk.schema(schema, "schema $name") }
    paths.orEmpty().forEach { (path, item) ->
        walk.check(item.extensions, "path $path")
        item.readOperationsMap().orEmpty().forEach { (method, operation) ->
            val where = "$method $path"
            walk.check(operation.extensions, where)
            operation.parameters.orEmpty().forEach { walk.parameter(it, where) }
            operation.requestBody?.let { body ->
                walk.check(body.extensions, "$where request body")
                body.content.orEmpty().forEach { (type, media) -> walk.schema(media.schema, "$where $type body") }
            }
            operation.responses.orEmpty().forEach { (code, response) ->
                walk.check(response.extensions, "$where response $code")
                response.content.orEmpty().forEach { (type, media) -> walk.schema(media.schema, "$where response $code $type") }
            }
        }
    }
}

private class NamespaceWalk {
    // By identity: `$ref` resolution shares instances, and structural equality would skip a second
    // schema that happens to look like one already seen.
    private val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Schema<*>, Boolean>())

    fun check(
        extensions: Map<String, Any?>?,
        where: String,
    ) {
        extensions.orEmpty().keys.forEach { key ->
            if (!key.startsWith(Ext.KOTLIN_PREFIX) || key in Ext.kotlinKeys) return@forEach
            throw OpenApiParseException("$where: unknown extension '$key'${suggestion(key)}")
        }
        // Reading each known key here as well means every later read is already known to be of the
        // right type, and the complaint carries the place in the document rather than the place in
        // the parser that happened to look first.
        extensions.kotlinName(where)
        extensions.externalType(where)
        extensions.extensionBoolean(Ext.SKIP, where)
        extensions.extensionBoolean(Ext.VALUE_CLASS, where)
    }

    fun parameter(
        parameter: io.swagger.v3.oas.models.parameters.Parameter,
        where: String,
    ) {
        val named = "$where parameter '${parameter.name}'"
        check(parameter.extensions, named)
        schema(parameter.schema, named)
    }

    fun schema(
        schema: Schema<*>?,
        where: String,
    ) {
        if (schema == null || !seen.add(schema)) return
        check(schema.extensions, where)
        schema.properties.orEmpty().forEach { (name, property) -> schema(property, "$where property '$name'") }
        schema(schema.items, "$where item")
        (schema.additionalProperties as? Schema<*>)?.let { schema(it, "$where value") }
        (schema.allOf.orEmpty() + schema.oneOf.orEmpty() + schema.anyOf.orEmpty())
            .forEach { schema(it, "$where branch") }
    }
}

/** Names the nearest key that does exist, because a typo is what an unknown key almost always is. */
private fun suggestion(key: String): String {
    val nearest = Ext.kotlinKeys.minByOrNull { distance(key, it) } ?: return ""
    return if (distance(key, nearest) <= 4) ". Did you mean '$nearest'?" else ""
}

private fun distance(
    a: String,
    b: String,
): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
        }
        previous = current
    }
    return previous[b.length]
}
