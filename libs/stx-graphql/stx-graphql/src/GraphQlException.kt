package com.strange.graphql

/** A schema that cannot be built, or an execute call that cannot even be submitted. */
class GraphQlException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
