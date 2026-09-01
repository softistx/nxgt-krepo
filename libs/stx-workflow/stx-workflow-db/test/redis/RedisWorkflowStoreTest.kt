package com.strange.workflow.redis

import com.strange.workflow.WorkflowStatus
import com.strange.workflow.db.record
import com.strange.workflow.db.storeContract
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

class RedisWorkflowStoreTest :
    FeatureSpec({
        storeContract("Redis", RedisTestServer.available) { lease, block ->
            RedisTestServer.withRedis { redis -> block(RedisWorkflowStore(redis, lease = lease)) }
        }

        feature("what only the Redis store does").config(enabled = RedisTestServer.available) {
            scenario("the version is a field of the hash rather than part of the document") {
                RedisTestServer.withRedis { redis ->
                    RedisWorkflowStore(redis).create(record("a"))

                    // The conditional write compares it in Lua, before anything decodes the
                    // document — so there is one copy of it and the script never parses JSON.
                    redis.commands.hget(redis.instanceKey("a"), "record")!!.contains("\"version\"") shouldBe false
                }
            }

            scenario("a finished instance is kept only as long as the retention says") {
                RedisTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis, retention = 30.minutes)
                    store.create(record("done"))
                    store.create(record("stuck"))

                    val done = store.load("done")!!
                    store.save(done.copy(status = WorkflowStatus.Completed), done.version)
                    val stuck = store.load("stuck")!!
                    store.save(stuck.copy(status = WorkflowStatus.Failed), stuck.version)

                    val ttl = redis.commands.pttl(redis.instanceKey("done")) ?: -1L
                    (ttl > 0L) shouldBe true
                    // One that needs a person is exempt: expiring it deletes the only description
                    // of what has to be fixed. The relational store says the same thing by never
                    // stamping finished_at on it.
                    redis.commands.pttl(redis.instanceKey("stuck")) shouldBe -1L
                }
            }
        }
    })
