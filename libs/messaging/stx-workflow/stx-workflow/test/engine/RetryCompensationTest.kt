package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowConflictException
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowNotFoundException
import com.softistx.workflow.WorkflowNotRetryableException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.compensate
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
import java.util.concurrent.atomic.AtomicBoolean

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
                val both = WorkflowEngine(InMemoryStore()) {
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
    })
