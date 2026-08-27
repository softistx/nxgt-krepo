package com.strange.redis

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * The Redis this workspace already runs, not one a test starts —
 * `~/workspace/docker/apps/database/redis` publishes it on the default port, and `REDIS_TEST_URI`
 * points the tests somewhere else when needed.
 *
 * Two habits keep a shared instance shared. The tests use **database 15**, so nothing they write
 * lands next to another application's keys in db 0; and each spec gets a [RedisConfig.namespace] of
 * its own, deleted afterwards. Neither ever calls `FLUSHDB` — the whole point of a long-lived
 * server is that it holds someone else's data too.
 */
internal object RedisTestServer {
    private val uri = System.getenv("REDIS_TEST_URI") ?: "redis://localhost:6379/15"

    private val namespaces = AtomicInteger()

    private fun config(namespace: String = "") = RedisConfig(uri, namespace, timeout = 2.seconds)

    val available: Boolean by lazy {
        runCatching { Redis.connect(config()).use { redis -> runBlocking { redis.ping() } } }.isSuccess
    }

    /** Runs [block] against a namespace no other spec is using, and deletes it afterwards. */
    suspend fun withRedis(block: suspend (Redis) -> Unit) {
        Redis.connect(config("shared-redis-test:${namespaces.incrementAndGet()}")).use { redis ->
            try {
                block(redis)
            } finally {
                val keys = redis.commands.keys("${redis.namespace}*").toList()
                if (keys.isNotEmpty()) redis.commands.del(*keys.toTypedArray())
            }
        }
    }
}
