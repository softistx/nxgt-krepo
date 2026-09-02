package com.softistx.redis.codec

import com.softistx.common.serialization.lenientJson
import kotlinx.serialization.json.Json

/**
 * What a stored value is serialized through unless the caller says otherwise.
 *
 * The shared [lenientJson]: unknown keys are ignored, because a cached value was written by
 * whoever deployed last and is read by whoever deployed first. Configured once on `RedisConfig`,
 * so a service decides this for its Redis values in one place rather than per cache, topic and
 * stream.
 */
val redisJson: Json = lenientJson
