package com.softistx.graphix.schema

import com.softistx.graphix.GraphixBuilder
import graphql.TypeResolutionEnvironment

/**
 * An application's answer for one interface or union: the GraphQL **object type name** a value
 * should be, or `null` to let it fail as a GraphQL error.
 *
 * A name rather than a `GraphQLObjectType` keeps graphql-java's schema types out of application
 * code and keeps the "not in the schema" error in one place — the same trade the `scalar { }`
 * DSL makes.
 */
fun interface GraphixTypeName {
    fun TypeResolutionEnvironment.resolve(value: Any?): String?
}

/**
 * Overrides how Graphix resolves the interface or union named [typeName].
 *
 * Only needed when the runtime Kotlin class name is not the GraphQL type name — by default the
 * class's [GraphQLName], or its simple name, is the answer.
 *
 * ```kotlin
 * Graphix {
 *     typeResolver("SearchResult") { value -> if (value is Row) "Product" else "Review" }
 *     resolvers(SearchQueries(store))
 * }
 * ```
 */
fun GraphixBuilder.typeResolver(
    typeName: String,
    resolve: TypeResolutionEnvironment.(value: Any?) -> String?,
) {
    typeResolver(typeName, GraphixTypeName { value -> resolve(value) })
}

/** Registers [resolver] for [typeName]. A second resolver for the same type fails schema build. */
fun GraphixBuilder.typeResolver(
    typeName: String,
    resolver: GraphixTypeName,
) {
    addTypeResolver(typeName, resolver)
}
