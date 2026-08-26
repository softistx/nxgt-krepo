package com.strange.openapi.parser

/** Name derivation shared by every emitter, so generated names stay stable across clients. */
public object Naming {

    private val separators = Regex("[^A-Za-z0-9]+")

    /**
     * `categories-controller` -> `CategoriesApi`, `users` -> `UsersApi`.
     *
     * A `-controller` suffix is dropped first: it names the server class in specs generated from
     * one, and carrying it into a client interface reads wrong.
     */
    public fun interfaceName(tag: String, naming: InterfaceNaming = InterfaceNaming()): String {
        val cleaned = tag.removeSuffix("-controller").removeSuffix("Controller")
        return naming.prefix + pascal(cleaned).ifEmpty { "Default" } + naming.suffix
    }

    /** `findCategories` stays as-is; `find-categories` and `find_categories` become camelCase. */
    public fun functionName(operationId: String): String = camel(operationId)

    /** Property and parameter names: `postId` stays, `post_id` becomes `postId`. */
    public fun propertyName(wireName: String): String = camel(wireName)

    public fun pascal(value: String): String =
        value.split(separators)
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    public fun camel(value: String): String =
        pascal(value).replaceFirstChar { it.lowercaseChar() }
}
