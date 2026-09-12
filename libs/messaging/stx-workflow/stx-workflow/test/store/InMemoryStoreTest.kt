package com.softistx.workflow.store

import com.softistx.workflow.WorkflowStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private fun record(
    id: String,
    status: WorkflowStatus = WorkflowStatus.Running,
): WorkflowRecord {
    val now = Clock.System.now()
    return WorkflowRecord(id, "spec", status, JsonObject(emptyMap()), createdAt = now, updatedAt = now)
}

class InMemoryStoreTest :
    FeatureSpec({
        feature("the store contract") {
            scenario("a record goes in and comes back") {
                val store = InMemoryStore()
                store.create(record("a"))
                store.load("a")!!.workflow shouldBe "spec"
                store.load("nobody") shouldBe null
            }

            scenario("creating the same id twice is refused") {
                val store = InMemoryStore()
                store.create(record("a"))
                shouldThrow<IllegalArgumentException> { store.create(record("a")) }
            }

            scenario("a write on a stale version is refused, and the stored record is untouched") {
                val store = InMemoryStore()
                store.create(record("a"))
                val loaded = store.load("a")!!

                store.save(loaded.copy(status = WorkflowStatus.Completed), loaded.version) shouldBe true
                // A second writer holding the same stale copy loses.
                store.save(loaded.copy(status = WorkflowStatus.Cancelled), loaded.version) shouldBe false
                store.load("a")!!.status shouldBe WorkflowStatus.Completed
            }

            scenario("a successful write bumps the version") {
                val store = InMemoryStore()
                store.create(record("a"))
                store.save(store.load("a")!!, 0) shouldBe true
                store.load("a")!!.version shouldBe 1L
            }
        }

        feature("what is runnable") {
            scenario("terminal instances are not, and the rest are oldest first and bounded") {
                val store = InMemoryStore()
                store.create(record("running"))
                store.create(record("done", WorkflowStatus.Completed))
                store.create(record("failed", WorkflowStatus.Failed))

                store.runnable(Clock.System.now(), limit = 10) shouldBe listOf("running")
                store.runnable(Clock.System.now(), limit = 0) shouldBe emptyList()
            }

            scenario("one that is not due yet is not offered") {
                val store = InMemoryStore()
                val now = Clock.System.now()
                store.create(record("later").copy(wakeAt = now + 5.minutes))

                store.runnable(now, limit = 10) shouldBe emptyList()
                store.runnable(now + 10.minutes, limit = 10) shouldBe listOf("later")
            }
        }

        feature("the instance lock") {
            scenario("a second holder is told no rather than made to wait") {
                val store = InMemoryStore()
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

            scenario("the lock is free again once the block ends, however it ended") {
                val store = InMemoryStore()
                shouldThrow<IllegalStateException> { store.guarded("a") { error("boom") } }
                store.guarded("a") { "free" } shouldBe "free"
            }
        }
    })
