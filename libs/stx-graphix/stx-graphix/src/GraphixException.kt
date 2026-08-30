package com.strange.graphix

/**
 * A schema that cannot be built, or an execute call that cannot even be submitted.
 *
 * A resolver that throws is **not** this: that becomes a [GraphixError] on [GraphixResult].
 * This is a missing `@Serializable`, a duplicate field name, no query root, or a
 * `@GraphQLContext` parameter whose type was not in `execute`'s context map.
 */
class GraphixException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
