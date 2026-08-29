package com.strange.mongo

/**
 * What this module throws. Every failure here is about the *request*, not the connection — a
 * driver `MongoException` still comes through untouched, because a caller that cannot reach the
 * cluster needs to see that and not a rewrapped version of it.
 */
sealed class MongoDataException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** No document with [id] in [collection]. */
class DocumentNotFoundException(
    val collection: String,
    val id: String,
) : MongoDataException("No document '$id' in '$collection'")

/** The pagination arguments cannot be honoured — see `com.strange.mongo.page`. */
class InvalidPaginationException(
    message: String,
) : MongoDataException(message)
