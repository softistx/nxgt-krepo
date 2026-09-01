package com.strange.workflow.redis

import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import com.strange.redis.codec.redisJson
import com.strange.redis.deleteKeys
import com.strange.testing.containers.TestNames
import com.strange.testing.containers.redisContainer
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * The Redis these specs talk to: the workspace's own when `REDIS_TEST_URI` names it, one started
 * for the run otherwise.
 *
 * The two habits are `stx-redis`'s and they earn their keep here for the same reasons. Database 15,
 * so a run pointed at a real server writes nowhere near db 0; and a namespace per spec, deleted
 * afterwards, so two specs cannot see each other's instances. Nothing here calls `FLUSHDB`.
 */
internal object WorkflowTestServer {
    private val redis = redisContainer()
    private val namespaces = TestNames("stx-workflow-test", separator = ":")

    private fun config(namespace: String) = RedisConfig(redis.requireEndpoint(), namespace, timeout = 2.seconds, json = redisJson)

    val available: Boolean by lazy {
        redis.available &&
            runCatching { Redis.connect(config("")).use { server -> runBlocking { server.ping() } } }.isSuccess
    }

    suspend fun withRedis(block: suspend (Redis) -> Unit) {
        Redis.connect(config(namespaces.next())).use { redis ->
            try {
                block(redis)
            } finally {
                redis.deleteKeys()
            }
        }
    }
}
