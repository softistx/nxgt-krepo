package com.strange.openapi.parser

/** How operations are split into interfaces. */
public enum class Grouping { Tag, Path, None }

/**
 * How a group's name becomes an interface name: `categories-controller` -> `CategoriesApi`,
 * or `ICategoriesClient` with `prefix = "I"` and `suffix = "Client"`.
 */
public data class InterfaceNaming(
    val prefix: String = "",
    val suffix: String = "Api",
)
