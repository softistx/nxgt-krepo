package com.softistx.workflow.db

import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.store.WorkflowRecord
import com.softistx.workflow.store.WorkflowStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.scopes.FeatureSpecRootScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun record(
    id: String,
    status: WorkflowStatus = WorkflowStatus.Running,
    wakeAt: Instant? = null,
): WorkflowRecord {
    val now = Clock.System.now()
    return WorkflowRecord(id, "spec", status, JsonObject(emptyMap()), wakeAt = wakeAt, createdAt = now, updatedAt = now)
}

/**
 * What every `WorkflowStore` has to answer for, asked of each of them.
 *
 * Written once and run three times, which is the reason these implementations share a module rather
 * than having one apiece. The engine has one set of rules — a stale write is *refused* and not
 * thrown at, a parked instance is offered to nobody, a lease declines rather than queues — and a
 * second store that read any of them differently would be a second engine with the same name. Three
 * copies of this file would have said so three times and drifted the first time one was fixed.
 *
 * [store] hands the block a store over a server the spec's own harness prepared, with [lease] set,
 * and cleans up afterwards. Everything a particular backend does *beyond* the contract — a TTL, a
 * Lua script, a column an operator can read — belongs in that store's own spec, not here.
 */
internal fun FeatureSpecRootScope.storeContract(
    label: String,
    enabled: Boolean,
    store: suspend (Duration, suspend (WorkflowStore) -> Unit) -> Unit,
) {
    feature("the store contract, on $label").config(enabled = enabled) {
        scenario("a record goes in and comes back, with its version beside it rather than inside it") {
            store(30.seconds) { store ->
                store.create(record("a"))

                val loaded = store.load("a")!!
                loaded.workflow shouldBe "spec"
                loaded.version shouldBe 0L
                store.load("nobody") shouldBe null
            }
        }

        scenario("creating the same id twice is refused") {
            store(30.seconds) { store ->
                store.create(record("a"))
                shouldThrow<IllegalArgumentException> { store.create(record("a")) }
            }
        }

        scenario("a write on a stale version is refused, and the stored record is untouched") {
            store(30.seconds) { store ->
                store.create(record("a"))
                val loaded = store.load("a")!!

                store.save(loaded.copy(status = WorkflowStatus.Compensating), loaded.version) shouldBe true
                store.save(loaded.copy(status = WorkflowStatus.Cancelled), loaded.version) shouldBe false

                val stored = store.load("a")!!
                stored.status shouldBe WorkflowStatus.Compensating
                stored.version shouldBe 1L
            }
        }

        scenario("a running instance is offered again only once its lease has passed") {
            store(30.seconds) { store ->
                store.create(record("a"))

                // Just checkpointed: somebody is plainly working on it, so it is not offered.
                store.runnable(Clock.System.now(), 10).shouldBeEmpty()
                store.runnable(Clock.System.now() + 1.minutes, 10) shouldBe listOf("a")
            }
        }

        scenario("an instance due at a moment is offered then, and not before") {
            store(30.seconds) { store ->
                val wake = Clock.System.now() + 1.minutes
                store.create(record("a", WorkflowStatus.Sleeping, wakeAt = wake))

                store.runnable(wake - 1.seconds, 10).shouldBeEmpty()
                store.runnable(wake + 1.seconds, 10) shouldBe listOf("a")
            }
        }

        scenario("an instance parked on a signal is offered to nobody, ever") {
            store(30.seconds) { store ->
                // No wakeAt: only engine.signal or engine.cancel will move this one, so a worker
                // polling for it would spend its life re-parking the same instance.
                store.create(record("a", WorkflowStatus.Awaiting))

                store.runnable(Clock.System.now() + 365.days, 10).shouldBeEmpty()
            }
        }

        scenario("a finished instance leaves the index") {
            store(30.seconds) { store ->
                store.create(record("a"))
                val loaded = store.load("a")!!
                store.save(loaded.copy(status = WorkflowStatus.Completed), loaded.version) shouldBe true

                store.runnable(Clock.System.now() + 1.minutes, 10).shouldBeEmpty()
            }
        }

        scenario("it offers no more than it was asked for") {
            store(30.seconds) { store ->
                repeat(5) { store.create(record("a$it")) }

                store.runnable(Clock.System.now() + 1.minutes, 2).size shouldBe 2
                store.runnable(Clock.System.now() + 1.minutes, 0).shouldBeEmpty()
            }
        }

        scenario("it finds the instances that need a person, newest first") {
            store(30.seconds) { store ->
                // Three finished instances and one still going. Written oldest-first so the order
                // that comes back is the store's doing, not the insertion order's.
                val now = Clock.System.now()
                listOf("old" to 3, "middle" to 2, "new" to 1).forEach { (id, agoMinutes) ->
                    store.create(record(id))
                    val loaded = store.load(id)!!
                    store.save(
                        loaded.copy(status = WorkflowStatus.Failed, updatedAt = now - agoMinutes.minutes),
                        loaded.version,
                    ) shouldBe true
                }
                store.create(record("running"))

                store.find(WorkflowStatus.Failed).map { it.id } shouldBe listOf("new", "middle", "old")
                store.find(WorkflowStatus.Running).map { it.id } shouldBe listOf("running")
                store.find(WorkflowStatus.Completed).shouldBeEmpty()
            }
        }

        scenario("a page of them, and the page after it") {
            store(30.seconds) { store ->
                val now = Clock.System.now()
                repeat(5) { i ->
                    store.create(record("f$i"))
                    val loaded = store.load("f$i")!!
                    store.save(
                        loaded.copy(status = WorkflowStatus.Failed, updatedAt = now - (5 - i).minutes),
                        loaded.version,
                    ) shouldBe true
                }

                store.find(WorkflowStatus.Failed, limit = 2).map { it.id } shouldBe listOf("f4", "f3")
                store.find(WorkflowStatus.Failed, limit = 2, offset = 2).map { it.id } shouldBe listOf("f2", "f1")
                store.find(WorkflowStatus.Failed, limit = 0).shouldBeEmpty()
            }
        }

        scenario("an instance that moves on leaves the index it was in") {
            store(30.seconds) { store ->
                store.create(record("a"))
                val failed = store.load("a")!!
                store.save(failed.copy(status = WorkflowStatus.Failed), failed.version) shouldBe true
                store.find(WorkflowStatus.Failed).map { it.id } shouldBe listOf("a")

                // Somebody fixed it by hand and marked it done. An inbox that still showed it would
                // be an inbox nobody trusts.
                val fixed = store.load("a")!!
                store.save(fixed.copy(status = WorkflowStatus.Compensated), fixed.version) shouldBe true

                store.find(WorkflowStatus.Failed).shouldBeEmpty()
                store.find(WorkflowStatus.Compensated).map { it.id } shouldBe listOf("a")
            }
        }

        scenario("a second holder of one instance is told no rather than made to wait") {
            store(30.seconds) { store ->
                store.create(record("a"))
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

        scenario("and gets in once the first one is done") {
            store(30.seconds) { store ->
                store.create(record("a"))

                store.guarded("a") { "first" } shouldBe "first"
                store.guarded("a") { "second" } shouldBe "second"
            }
        }

        scenario("a lease is released even when the work throws") {
            store(30.seconds) { store ->
                store.create(record("a"))

                shouldThrow<IllegalStateException> { store.guarded<Unit>("a") { error("boom") } }

                store.guarded("a") { "after" } shouldBe "after"
            }
        }
    }
}
