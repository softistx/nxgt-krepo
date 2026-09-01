package com.strange.workflow.engine

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.step
import com.strange.workflow.fixture.Calls
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * A start booked for later.
 *
 * The instance exists from the call — an id, a context, a record somebody can cancel — and has run
 * nothing. What separates it from an instance that is merely between two nodes is that it has no
 * journal at all, which is a fact about the design rather than a flag: every node that runs writes
 * an entry.
 */
class ScheduledStartTest :
    FeatureSpec({
        fun reminding(calls: Calls) =
            workflow<Ledger>("reminding") {
                step("notify") {
                    calls.record("notify")
                    context.copy(note = "sent")
                } compensate {
                    calls.record("unsend")
                }
            }

        feature("booking one") {
            scenario("it exists, has run nothing, and says when it begins") {
                val calls = Calls()
                val flow = reminding(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val at = Clock.System.now() + 1.hours

                val instance = engine.startAt(flow, Ledger(), at = at, id = "r-1")

                instance.status shouldBe WorkflowStatus.Sleeping
                instance.record.wakeAt shouldBe at
                instance.record.journal.shouldBeEmpty()
                calls.all().shouldBeEmpty()
            }

            scenario("a worker is told about it, scored at the moment it begins") {
                val store = InMemoryStore()
                val flow = reminding(Calls())
                val engine = WorkflowEngine(store) { register(flow) }

                engine.startAt(flow, Ledger(), at = Clock.System.now() + 1.hours, id = "r-2")

                store.runnable(Clock.System.now(), limit = 10).shouldBeEmpty()
                store.runnable(Clock.System.now() + 2.hours, limit = 10) shouldBe listOf("r-2")
            }

            scenario("resuming it early runs nothing and leaves it booked") {
                val calls = Calls()
                val flow = reminding(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                engine.startAt(flow, Ledger(), at = Clock.System.now() + 1.hours, id = "r-3")

                // A store may offer an instance a little early. Running it then would make startAt
                // mean "about then", which is not what anybody books.
                val resumed = engine.resume("r-3")

                resumed.status shouldBe WorkflowStatus.Sleeping
                calls.all().shouldBeEmpty()
            }

            scenario("a moment that has already passed is due, not deferred") {
                val calls = Calls()
                val flow = reminding(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val instance = engine.startAt(flow, Ledger(), at = Clock.System.now() - 1.hours, id = "r-4")

                instance.status shouldBe WorkflowStatus.Completed
                calls.all() shouldBe listOf("notify")
            }
        }

        feature("the moment arriving") {
            scenario("it runs from the first node, as an ordinary instance") {
                val calls = Calls()
                val flow = reminding(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                engine.startAfter(flow, Ledger(), delay = 30.milliseconds, id = "r-5")

                delay(60)
                val record = engine.resume("r-5")

                record.status shouldBe WorkflowStatus.Completed
                record.wakeAt shouldBe null
                calls.all() shouldBe listOf("notify")
            }
        }

        feature("cancelling one before it begins") {
            scenario("it lands Cancelled with nothing to undo, because nothing ran") {
                val calls = Calls()
                val flow = reminding(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                engine.startAt(flow, Ledger(), at = Clock.System.now() + 1.hours, id = "r-6")

                val cancelled = engine.cancel("r-6")

                cancelled.status shouldBe WorkflowStatus.Cancelled
                calls.all().shouldBeEmpty()
                engine
                    .record("r-6")
                    .shouldNotBeNull()
                    .journal
                    .shouldBeEmpty()
            }
        }
    })
