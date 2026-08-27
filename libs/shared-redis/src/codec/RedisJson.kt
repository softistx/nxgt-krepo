package com.strange.redis.codec

import kotlinx.serialization.json.Json

/**
 * The `Json` every typed layer serializes through unless the connection was given another.
 *
 * Lenient about unknown keys because of what a Redis value is: a copy, not the record. One written
 * by the previous deploy, carrying a field this version has since dropped, should still read — the
 * alternative is a rolling deploy in which half the fleet cannot decode the other half's cache
 * entries. A caller who wants that skew to be loud instead passes a strict `Json` in `RedisConfig`,
 * and gets a `RedisValueException` naming the key it could not read.
 */
val redisJson: Json = Json { ignoreUnknownKeys = true }
