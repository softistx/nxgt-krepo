package com.strange.ktor.redis

import com.strange.ktor.own
import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey

/**
 * One Redis connection for the application, closed when it stops.
 *
 * ```kotlin
 * install(RedisConnection) { config = RedisConfig(uri = System.getenv("REDIS_URI"), namespace = "orders") }
 *
 * get("/cart/{id}") { call.respondText(call.redis.commands.get(call.redis.key("cart", id)) ?: "") }
 * ```
 *
 * A connection is a pool, and a pool is the thing you want exactly one of: opening one per request
 * spends a round trip on every call, and opening one per route leaves as many as there are routes.
 * Closing it is the half that gets forgotten, which costs nothing visible until a redeploy loop has
 * left a server holding connections nobody is on the other end of.
 */
val RedisConnection =
    createApplicationPlugin(name = "Redis", createConfiguration = ::RedisConnectionConfiguration) {
        application.own(RedisKey, Redis.connect(pluginConfig.config))
    }

/** What [RedisConnection] connects with. */
class RedisConnectionConfiguration {
    /**
     * The connection, its namespace and its `Json`.
     *
     * Defaulted rather than required, because [RedisConfig] is already the type that carries this
     * library's defaults — a second opinion about them here would only be a place for the two to
     * disagree.
     */
    var config: RedisConfig = RedisConfig()
}

internal val RedisKey = AttributeKey<Redis>("com.strange.redis.Redis")
