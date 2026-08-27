package com.strange.redis

import com.strange.redis.codec.redisJson
import com.strange.testing.containers.redisContainer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * The Redis the integration specs talk to: one started for this run, unless `REDIS_TEST_URI` names
 * a server that is already up — the workspace's own on `localhost:6379`, or one CI provisioned.
 *
 * Two habits survive from when this only ever ran against a shared server, and both still earn
 * their keep. The tests use **database 15**, so a run pointed at somebody's real Redis writes
 * nowhere near db 0; and each spec gets a [RedisConfig.namespace] of its own, deleted afterwards.
 * Neither ever calls `FLUSHDB` — against a shared server that would take out someone else's data,
 * and against a container it would hide a spec that failed to clean up after itself.
 */
internal object RedisTestServer {
    private val redis = redisContainer()

    private val namespaces = AtomicInteger()

    private fun config(
        namespace: String = "",
        json: Json = redisJson,
    ) = RedisConfig(
        requireNotNull(redis.endpoint) { redis.describe() },
        namespace,
        timeout = 2.seconds,
        json = json,
    )

    val available: Boolean by lazy {
        redis.available &&
            runCatching { Redis.connect(config()).use { server -> runBlocking { server.ping() } } }.isSuccess
    }

    /**
     * Runs [block] against a namespace no other spec is using, and deletes it afterwards.
     *
     * [json] is a parameter because the connection's own `Json` is a thing worth testing: a spec
     * that wants strict decoding asks for it here rather than building a `Redis` by hand.
     */
    suspend fun withRedis(
        json: Json = redisJson,
        block: suspend (Redis) -> Unit,
    ) {
        Redis.connect(config("shared-redis-test:${namespaces.incrementAndGet()}", json)).use { redis ->
            try {
                block(redis)
            } finally {
                val keys = redis.commands.keys("${redis.namespace}*").toList()
                if (keys.isNotEmpty()) redis.commands.del(*keys.toTypedArray())
            }
        }
    }
}
