package com.strange.workflow.redis

import com.strange.workflow.WorkflowStatus
import com.strange.workflow.store.WorkflowRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private fun record(
    id: String,
    status: WorkflowStatus = WorkflowStatus.Running,
): WorkflowRecord {
    val now = Clock.System.now()
    return WorkflowRecord(id, "spec", status, JsonObject(emptyMap()), createdAt = now, updatedAt = now)
}

class RedisWorkflowStoreTest :
    FeatureSpec({
        feature("the store contract, on Redis").config(enabled = RedisTestServer.available) {
            scenario("a record goes in and comes back, with its version beside it rather than inside it") {
                RedisTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis)
                    store.create(record("a"))

                    val loaded = store.load("a")!!
                    loaded.workflow shouldBe "spec"
                    loaded.version shouldBe 0L
                    store.load("nobody") shouldBe null

                    // The version is what the conditional write compares, so it is a field of the
                    // hash and never part of the document.
                    redis.commands.hget(redis.instanceKey("a"), "record")!!.contains("\"version\"") shouldBe false
                }
            }

            scenario("creating the same id twice is refused") {
                RedisTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis)
                    store.create(record("a"))
                    shouldThrow<IllegalArgumentException> { store.create(record("a")) }
                }
            }

            scenario("a write on a stale version is refused, and the stored record is untouched") {
                RedisTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis)
                    store.create(record("a"))
                    val loaded = store.load("a")!!

                    store.save(loaded.copy(status = WorkflowStatus.Compensating), loaded.version) shouldBe true
                    store.save(loaded.copy(status = WorkflowStatus.Cancelled), loaded.version) shouldBe false

                    val stored = store.load("a")!!
                    stored.status shouldBe WorkflowStatus.Compensating
                    stored.version shouldBe 1L
                }
            }
        }

        feature("the runnable index").config(enabled = RedisTestServer.available) {
            scenario("a running instance is offered again only once its lease has passed") {
                RedisTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis, lease = 200.milliseconds)
                    store.create(record("a"))

                    // Just checkpointed: somebody is plainly working on it, so it is not offered.
                    store.runnable(Clock.System.now(), 10) shouldBe emptyList()
                    store.runnable(Clock.System.now() + 1.minutes, 10) shouldBe listOf("a")
                }
            }

            scenario("a finished instance leaves the index") {
                RedisTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis, lease = 1.milliseconds)
                    store.create(record("a"))
                    val loaded = store.load("a")!!
                    store.save(loaded.copy(status = WorkflowStatus.Completed), loaded.version)

                    store.runnable(Clock.System.now() + 1.minutes, 10) shouldBe emptyList()
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
                    // of what has to be fixed.
                    redis.commands.pttl(redis.instanceKey("stuck")) shouldBe -1L
                }
            }
        }

        feature("the instance lock").config(enabled = RedisTestServer.available) {
            scenario("a second holder is told no rather than made to wait") {
                RedisTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis)
                    val inside = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()

                    coroutineScope {
                        val first =
                            async {
                                store.guarded("a") {
                                    inside.complete(Unit)
                                    release.await()
                                    "first"
                                }
                            }
                        inside.await()
                        store.guarded("a") { "second" } shouldBe null
                        release.complete(Unit)
                        first.await() shouldBe "first"
                    }
                }
            }
        }
    })
