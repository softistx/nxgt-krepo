package com.strange.redis

/**
 * What this module throws. A Lettuce `RedisException` still comes through untouched — a caller that
 * cannot reach the server needs to see that, and not a rewrapped version of it.
 */
sealed class RedisDataException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** A stored value could not be read back as the type that was asked for. */
class RedisValueException(
    message: String,
    cause: Throwable? = null,
) : RedisDataException(message, cause)

/** A lock could not be taken within the time allowed — see `com.strange.redis.lock`. */
class RedisLockException(
    message: String,
) : RedisDataException(message)
