package com.strange.graphix

/** A schema that cannot be built, or an execute call that cannot even be submitted. */
class GraphixException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
