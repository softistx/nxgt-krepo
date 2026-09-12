package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import java.net.URI

/**
 * An absolute URL, carried as a `java.net.URI`. `URI` and not `URL`, because `URL.equals` resolves
 * the host through DNS — a blocking network call from a data class's `equals`, which is the kind
 * of thing that is only ever found in production.
 *
 * Relative references are refused: a schema field called `url` that may hold `../thumb.png` has
 * not said anything a client can act on.
 */
internal val UrlScalar: GraphQLScalarType =
    scalarType(
        name = "Url",
        description = "An absolute URL.",
        specifiedBy = "https://www.rfc-editor.org/rfc/rfc3986",
        coercing =
            StringCoercing("Url", URI::class, { it.toString() }, { text ->
                URI(text).also { if (!it.isAbsolute) refuse() }
            }),
    )
