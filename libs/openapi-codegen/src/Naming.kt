package com.strange.openapi

/** Name derivation shared by every emitter, so generated names stay stable across clients. */
public object Naming {

    private val separators = Regex("[^A-Za-z0-9]+")

    /** `categories-controller` -> `CategoriesApi`, `users` -> `UsersApi`. */
    public fun interfaceName(tag: String): String {
        val cleaned = tag.removeSuffix("-controller").removeSuffix("Controller")
        return pascal(cleaned).ifEmpty { "Default" } + "Api"
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
