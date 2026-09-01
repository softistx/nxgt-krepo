package com.softistx.redis

import com.softistx.redis.codec.redisJson
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Where Redis is, and how this application is allowed to use it.
 *
 * [namespace] is the part worth explaining. Redis has no schemas — one instance is one flat
 * keyspace, usually shared by every service that was pointed at it — so every key this module
 * writes is prefixed. Two applications, or an application and its own tests, then share an instance
 * without being able to read or delete each other's keys by accident.
 *
 * The database index belongs in [uri] (`redis://host:6379/3`), because that is where Lettuce reads
 * it from and duplicating it here would only create a way for the two to disagree.
 *
 * [json] is the one every typed layer built on the connection serializes through, so a service
 * configures its Redis values once rather than once per cache, topic and stream. See [redisJson]
 * for what the default is lenient about and why.
 */
data class RedisConfig(
    val uri: String = "redis://localhost:6379",
    val namespace: String = "",
    val timeout: Duration = 10.seconds,
    val json: Json = redisJson,
)
