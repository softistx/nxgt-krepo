package com.strange.koin.redis

import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * One Redis connection for the container to hand out, closed when the container stops.
 *
 * ```kotlin
 * startKoin { modules(redisModule(RedisConfig(uri = System.getenv("REDIS_URI"))), appModule) }
 *
 * class CartStore(private val redis: Redis)
 * ```
 *
 * **Here the container creates it, unlike the Ktor plugins, which register what they installed.**
 * That is the difference between the two, and it follows from who is in charge: a worker or a CLI
 * on Koin has no `install` block to name a connection in, so the module is where it is named — and
 * `onClose` is then how it is closed, since the thing that created it is the thing that should.
 *
 * In an application that has both, install the plugin over this one rather than opening a second:
 * `install(RedisConnection) { instance = get() }`.
 */
fun redisModule(config: RedisConfig = RedisConfig()): Module =
    module {
        single { Redis.connect(config) } onClose { it?.close() }
    }
