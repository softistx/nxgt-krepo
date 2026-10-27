package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowConflictException
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowNotFoundException
import com.softistx.workflow.WorkflowNotRetryableException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.outcome
import com.softistx.workflow.dsl.parallel
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.fixture.Wobble
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.store.NodeOutcome
import com.softistx.workflow.workflow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean

private val CHARGE = outcome<String>("charge")
private val COURIER = outcome<String>("courier")

class RetryCompensationTest :
    FeatureSpec({
        /** Three reservations, then a failing step; 'b' is the compensation that can be made to fail. */
        fun saga(
            calls: Calls,
            bBroken: AtomicBoolean,
        ) = workflow<Ledger>("saga") {
            step("a") { context } compensate { calls.record("undo:a") }
            step("b") { context } compensate {
                calls.record("undo:b")
                if (bBroken.get()) throw Wobble("refund refused")
            }
            step("c") { context } compensate { calls.record("undo:c") }
            step("d") { throw Wobble("no courier") }
        }

        feature("retrying a compensation that failed") {
            scenario("the unwind continues where it stopped and does not repeat what already succeeded") {
                val calls = Calls()
                val broken = AtomicBoolean(true)
                val flow = saga(calls, broken)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val failed = engine.start(flow, Ledger())
                failed.status shouldBe WorkflowStatus.Failed
                calls.all() shouldBe listOf("undo:c", "undo:b")

                broken.set(false)
                val retried = engine.retryCompensation(failed.id)

                retried.status shouldBe WorkflowStatus.Compensated
                // 'c' is not run again; 'b' is retried, and 'a' runs now that nothing blocks it.
                calls.all() shouldBe listOf("undo:c", "undo:b", "undo:b", "undo:a")
                retried.error!!.node shouldBe "d"
                retried.latest("b")!!.outcome shouldBe NodeOutcome.Compensated
                retried.latest("a")!!.outcome shouldBe NodeOutcome.Compensated
                engine.find(WorkflowStatus.Failed) shouldBe emptyList()
            }

            scenario("a compensation that still fails leaves the instance Failed, and it can be retried again") {
                val calls = Calls()
                val broken = AtomicBoolean(true)
                val flow = saga(calls, broken)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                engine.retryCompensation(id).status shouldBe WorkflowStatus.Failed
                calls.all() shouldBe listOf("undo:c", "undo:b", "undo:b")
                engine.find(WorkflowStatus.Failed).map { it.id } shouldBe listOf(id)

                broken.set(false)
                engine.retryCompensation(id).status shouldBe WorkflowStatus.Compensated
                calls.count("undo:c") shouldBe 1
                calls.count("undo:a") shouldBe 1
            }

            scenario("an undo that failed lands Cancelled once retried, because it was asked for") {
                val calls = Calls()
                val broken = AtomicBoolean(false)
                val flow =
                    workflow<Ledger>("returnable") {
                        step("charge") { context } compensate {
                            calls.record("refund")
                            if (broken.get()) throw Wobble("refund refused")
                        }
                    }
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                broken.set(true)
                engine.undo(id).status shouldBe WorkflowStatus.Failed
                broken.set(false)

                engine.retryCompensation(id).status shouldBe WorkflowStatus.Cancelled
                calls.all() shouldBe listOf("refund", "refund")
            }
        }

        feature("what retryCompensation refuses") {
            scenario("an instance that is not Failed") {
                val flow =
                    workflow<Ledger>("plain") {
                        step("one") { context }
                    }
                val failing =
                    workflow<Ledger>("clean") {
                        step("one") { context } compensate { }
                        step("two") { throw Wobble() }
                    }
                val both =
                    WorkflowEngine(InMemoryStore()) {
                        register(flow)
                        register(failing)
                    }
                val completed = both.start(flow, Ledger()).id
                val compensated = both.start(failing, Ledger()).id

                shouldThrow<WorkflowNotRetryableException> { both.retryCompensation(completed) }.status shouldBe
                    WorkflowStatus.Completed
                shouldThrow<WorkflowNotRetryableException> { both.retryCompensation(compensated) }.status shouldBe
                    WorkflowStatus.Compensated
                shouldThrow<WorkflowNotFoundException> { both.retryCompensation("nope") }
            }

            scenario("a Failed instance with no failed compensation, such as one whose context did not decode") {
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("undecodable") {
                        step("one") { context }
                    }
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                val done = store.load(id)!!
                store.save(done.copy(status = WorkflowStatus.Failed), done.version)

                shouldThrow<WorkflowNotRetryableException> { engine.retryCompensation(id) }.status shouldBe
                    WorkflowStatus.Failed
                engine.record(id)!!.status shouldBe WorkflowStatus.Failed
            }

            scenario("a Failed instance with a failed compensation whose context no longer decodes is left untouched") {
                val calls = Calls()
                val store = InMemoryStore()
                val flow = saga(calls, AtomicBoolean(true))
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                val failed = store.load(id)!!
                failed.journal.any { it.outcome == NodeOutcome.CompensationFailed } shouldBe true
                store.save(failed.copy(context = JsonPrimitive("not a ledger")), failed.version)
                val before = store.load(id)!!

                val refused = shouldThrow<WorkflowNotRetryableException> { engine.retryCompensation(id) }
                refused.status shouldBe WorkflowStatus.Failed
                refused.message!!.contains("repair the stored context") shouldBe true

                val after = engine.record(id)!!
                after.status shouldBe WorkflowStatus.Failed
                after.error shouldBe before.error
                after.error!!.node shouldBe "d"
                after.journal shouldBe before.journal
                after.version shouldBe before.version
                calls.all() shouldBe listOf("undo:c", "undo:b")
            }

            scenario("an instance another process is advancing is a conflict, not a silent no-op") {
                val calls = Calls()
                val flow = saga(calls, AtomicBoolean(true))
                val store = InMemoryStore()
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                store.guarded(id) {
                    shouldThrow<WorkflowConflictException> { engine.retryCompensation(id) }
                }
            }
        }
        feature("retrying under the other paths an unwind can take") {
            scenario("two retries at once: one runs the compensation, the other is told no") {
                val calls = Calls()
                val reached = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val broken = AtomicBoolean(true)
                val slow = AtomicBoolean(false)
                val flow =
                    workflow<Ledger>("racing") {
                        step("a") { context } compensate {
                            calls.record("undo:a")
                            if (broken.get()) throw Wobble("refund refused")
                            if (slow.get()) {
                                reached.complete(Unit)
                                release.await()
                            }
                        }
                        step("b") { throw Wobble("no courier") }
                    }
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                broken.set(false)
                slow.set(true)

                val first = async(Dispatchers.Default) { engine.retryCompensation(id) }
                reached.await()
                // The first retry holds the instance and is inside the compensation.
                shouldThrow<WorkflowConflictException> { engine.retryCompensation(id) }
                release.complete(Unit)

                first.await().status shouldBe WorkflowStatus.Compensated
                // And once it is done there is nothing left to retry.
                shouldThrow<WorkflowNotRetryableException> { engine.retryCompensation(id) }.status shouldBe
                    WorkflowStatus.Compensated
                calls.all() shouldBe listOf("undo:a", "undo:a")
            }

            scenario("a fan-out leg whose compensation failed is retried with the value it produced") {
                val calls = Calls()
                val broken = AtomicBoolean(true)
                val flow =
                    workflow<Ledger>("legs") {
                        parallel("provision") {
                            branch(CHARGE) { "c-1" } compensate { id ->
                                calls.record("undo:charge:$id")
                                if (broken.get()) throw Wobble("refund refused")
                            }
                            branch(COURIER) { "b-1" } compensate { id -> calls.record("undo:courier:$id") }
                            merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
                        }
                        step("ship") { throw Wobble("no courier") }
                    }
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                engine.record(id)!!.status shouldBe WorkflowStatus.Failed

                broken.set(false)
                val retried = engine.retryCompensation(id)

                retried.status shouldBe WorkflowStatus.Compensated
                calls.all().filter { it.startsWith("undo:charge") } shouldBe
                    listOf("undo:charge:c-1", "undo:charge:c-1")
                calls.all().filter { it.startsWith("undo:courier") } shouldBe listOf("undo:courier:b-1")
            }

            scenario("a cancel whose compensation failed lands Cancelled once retried") {
                val calls = Calls()
                val broken = AtomicBoolean(true)
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("cancellable") {
                        step("reserve") { context } compensate {
                            calls.record("undo:reserve")
                            if (broken.get()) throw Wobble("held")
                        }
                        step("wait") { context }
                    }
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                val done = store.load(id)!!
                // Put back to how it looked mid-run, as the cancel spec does.
                store.save(done.copy(status = WorkflowStatus.Running, journal = done.journal.take(1)), done.version)

                engine.cancel(id).status shouldBe WorkflowStatus.Failed
                broken.set(false)

                engine.retryCompensation(id).status shouldBe WorkflowStatus.Cancelled
                calls.all() shouldBe listOf("undo:reserve", "undo:reserve")
            }
        }
    })
