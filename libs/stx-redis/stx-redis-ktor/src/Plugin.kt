package com.softistx.redis.ktor

import com.softistx.ktor.resource
import com.softistx.redis.Redis
import com.softistx.redis.RedisConfig
import io.ktor.server.application.*
import io.ktor.util.*

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
        application.resource(RedisKey, pluginConfig.instance) { Redis.connect(pluginConfig.config) }
        if (pluginConfig.injectable) application.provideRedis()
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

    /**
     * A connection built elsewhere — by a DI container, or by hand.
     *
     * When set, [config] is ignored and this is **not** closed when the application stops: whoever created
     * it closes it. That is what lets a container own the connection while routes still reach it
     * through `call.redis`.
     */
    var instance: Redis? = null

    /**
     * Registers the connection with Ktor's DI as well, so a class the container builds can take a
     * [Redis] in its constructor — the same one `call.redis` hands a route.
     *
     * Off by default, and it has to be: `ktor-server-di` is compile-only in this module, so an
     * application that never asks for this must not be made to carry it at runtime. Setting it
     * calls [provideRedis], which lives in its own file for that reason — nothing loads a class
     * from Ktor's DI until the flag is true.
     *
     * The container closes what it hands out when the application stops, so this hands it a second
     * claim on closing the connection. That is safe — these clients close idempotently — but a
     * connection that has to outlive the application does not belong in it.
     */
    var injectable: Boolean = true
}

internal val RedisKey = AttributeKey<Redis>("com.softistx.redis.Redis")
