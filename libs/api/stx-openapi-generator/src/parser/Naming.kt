package com.softistx.openapi.parser

/** Name derivation shared by every emitter, so generated names stay stable across clients. */
public object Naming {
    private val separators = Regex("[^A-Za-z0-9]+")

    /**
     * The boundary inside `orderId`, so an endpoint constant reads `ORDER_ID` and not `ORDERID`.
     *
     * Applied only by [endpointConstant]. [enumEntry] deliberately leaves camelCase alone: it names
     * entries after wire values that are already published, and splitting them would rename every
     * enum a document declares in that style.
     */
    private val camelHump = Regex("([a-z0-9])([A-Z])")

    /**
     * `categories-controller` -> `CategoriesApi`, `users` -> `UsersApi`.
     *
     * A `-controller` suffix is dropped first: it names the server class in specs generated from
     * one, and carrying it into a client interface reads wrong.
     */
    public fun interfaceName(
        tag: String,
        naming: InterfaceNaming = InterfaceNaming(),
    ): String {
        val cleaned = tag.removeSuffix("-controller").removeSuffix("Controller")
        return naming.prefix + pascal(cleaned).ifEmpty { "Default" } + naming.suffix
    }

    /** `findCategories` stays as-is; `find-categories` and `find_categories` become camelCase. */
    public fun functionName(operationId: String): String = camel(operationId)

    /** Property and parameter names: `postId` stays, `post_id` becomes `postId`. */
    public fun propertyName(wireName: String): String = camel(wireName)

    /**
     * A wire value as an enum entry name: `active` -> `ACTIVE`, `in-progress` -> `IN_PROGRESS`.
     *
     * Kotlin entry names cannot start with a digit and cannot be empty, and a wire value is under
     * no obligation to respect either — `2xx` and `""` are both legal in a document.
     */
    public fun enumEntry(wireValue: String): String {
        val parts = wireValue.split(separators).filter { it.isNotEmpty() }
        val joined = parts.joinToString("_") { it.uppercase() }
        return when {
            joined.isEmpty() -> "EMPTY"
            joined.first().isDigit() -> "V$joined"
            else -> joined
        }
    }

    /**
     * A verb and a path as a constant name: `PATCH` + `orders/{id}/status` -> `PATCH_ORDERS_ID_STATUS`.
     *
     * The template braces are separators like any other punctuation, which is what makes the name
     * readable — and also what makes it ambiguous, since `orders/{id}` and `orders/id` reduce to the
     * same thing. `NameCollisions.kt` reports that rather than letting two identical properties into
     * one object; `x-kotlin-endpoint` is the way out for a document that legitimately has both.
     */
    public fun endpointConstant(
        method: String,
        path: String,
    ): String = enumEntry("${method}_${path.replace(camelHump, "$1_$2")}")

    public fun pascal(value: String): String =
        value
            .split(separators)
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    public fun camel(value: String): String = pascal(value).replaceFirstChar { it.lowercaseChar() }
}
