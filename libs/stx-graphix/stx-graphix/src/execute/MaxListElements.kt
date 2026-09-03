package com.softistx.graphix.execute

import graphql.schema.DataFetchingEnvironment

/**
 * Context key for `GraphixBuilder.maxListElements`. Absent means unbounded, which is what a `List`
 * return already is.
 *
 * A context key rather than a parameter on `resolverFetcher`: that signature has six call sites
 * across two schema builders, and this leaves room for a per-operation override later, the way
 * `GraphixMessages` and `GraphixLimits` already have one.
 */
internal object MaxListElements

internal fun DataFetchingEnvironment.maxListElements(): Int? = graphQlContext.get<Int>(MaxListElements)
