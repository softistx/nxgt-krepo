package com.strange.workflow.engine

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.step
import com.strange.workflow.fixture.Calls
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.fixture.Wobble
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.store.NodeOutcome
import com.strange.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class CompensationTest :
    FeatureSpec({
        feature("unwinding after a failure") {
            scenario("the steps that succeeded are undone newest first, and the one that failed is not") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("saga") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate
                            { calls.record("undo:reserve:${context.reservationId}") }
                        step("charge") { context.copy(chargeId = "c-1") } compensate
                            { calls.record("undo:charge:${context.chargeId}") }
                        step("ship") { throw Wobble("no courier") } compensate
                            { calls.record("undo:ship") }
                    }

                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val instance = engine.start(flow, Ledger())

                calls.all() shouldBe listOf("undo:charge:c-1", "undo:reserve:r-1")
                instance.status shouldBe WorkflowStatus.Compensated
                instance.error!!.node shouldBe "ship"
                instance.error!!.type shouldBe "com.strange.workflow.fixture.Wobble"
            }

            scenario("a step that declared no compensation is simply skipped by the unwind") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("partial") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate { calls.record("undo:reserve") }
                        step("note") { context.copy(note = "n") }
                        step("fail") { throw Wobble() }
                    }

                WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.all() shouldBe listOf("undo:reserve")
            }

            scenario("an instance left Failed is findable afterwards, which is the point of the status") {
                // Failed is the one outcome this engine refuses to resolve on its own: a
                // compensation that could not be made to work, waiting for a person. A status
                // nobody can search for is a status that says nothing, so this is the half that
                // makes it mean something.
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("inbox") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate { throw Wobble("held") }
                        step("charge") { throw Wobble("declined") }
                    }
                val engine = WorkflowEngine(store) { register(flow) }

                engine.start(flow, Ledger(), "stuck-1").status shouldBe WorkflowStatus.Failed
                engine.start(flow, Ledger(), "stuck-2").status shouldBe WorkflowStatus.Failed

                val inbox = engine.find(WorkflowStatus.Failed)
                inbox.map { it.id }.toSet() shouldBe setOf("stuck-1", "stuck-2")
                inbox.all { it.error != null } shouldBe true
                engine.find(WorkflowStatus.Completed) shouldBe emptyList()
            }

            scenario("a compensation that fails stops the unwind and leaves the instance Failed") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("stuck") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate { calls.record("undo:reserve") }
                        step("charge") { context.copy(chargeId = "c-1") } compensate { throw Wobble("refund refused") }
                        step("ship") { throw Wobble("no courier") }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                // 'reserve' is deliberately left alone: undoing the steps before one whose undo failed
                // is what produces a state nobody can describe.
                calls.all() shouldBe emptyList()
                instance.status shouldBe WorkflowStatus.Failed
                instance.record.latest("charge")!!.outcome shouldBe NodeOutcome.CompensationFailed
                instance.record.latest("reserve")!!.outcome shouldBe NodeOutcome.Succeeded
            }
        }

        feature("cancelling") {
            scenario("what already ran is undone, and the instance lands Cancelled") {
                val calls = Calls()
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("cancellable") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate { calls.record("undo:reserve") }
                        step("wait") { context.copy(note = "waiting") }
                    }

                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                // A completed instance is left alone; cancel is for one still in flight, so this one
                // is put back to how it looked mid-run.
                val running = store.load(id)!!
                store.save(running.copy(status = WorkflowStatus.Running, journal = running.journal.take(1)), running.version)

                val cancelled = engine.cancel(id)

                cancelled.status shouldBe WorkflowStatus.Cancelled
                calls.all() shouldBe listOf("undo:reserve")
            }
        }
    })
