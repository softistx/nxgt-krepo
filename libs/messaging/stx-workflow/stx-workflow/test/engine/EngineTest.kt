package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.store.NodeOutcome
import com.softistx.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class EngineTest :
    FeatureSpec({
        feature("a workflow of plain steps") {
            scenario("every step runs in order, and each one sees what the last one wrote") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("ordered") {
                        step("reserve") {
                            calls.record("reserve")
                            context.copy(reservationId = "r-1")
                        }
                        step("charge") {
                            calls.record("charge")
                            context.reservationId shouldBe "r-1"
                            context.copy(chargeId = "c-1")
                        }
                    }

                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val instance = engine.start(flow, Ledger())

                calls.all() shouldBe listOf("reserve", "charge")
                instance.status shouldBe WorkflowStatus.Completed
                instance.isOk shouldBe true
                instance.context shouldBe Ledger(reservationId = "r-1", chargeId = "c-1")
            }

            scenario("the journal names every node that succeeded, in the order they ran") {
                val flow =
                    workflow<Ledger>("journalled") {
                        step("one") { context.copy(note = "one") }
                        step("two") { context.copy(note = "two") }
                    }

                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val record = engine.start(flow, Ledger()).record

                record.journal.map { it.node to it.outcome } shouldBe
                    listOf("one" to NodeOutcome.Succeeded, "two" to NodeOutcome.Succeeded)
            }

            scenario("the stored context is written after each step, so it survives the run") {
                val store = InMemoryStore()
                val flow = workflow<Ledger>("stored") { step("only") { context.copy(note = "kept") } }

                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                engine.resume(flow, id).context.note shouldBe "kept"
            }
        }

        feature("registration") {
            scenario("starting a workflow the engine cannot look up again is refused") {
                val flow = workflow<Ledger>("unregistered") { step("only") { context } }
                val engine = WorkflowEngine(InMemoryStore())

                runCatching { engine.start(flow, Ledger()) }
                    .exceptionOrNull()!!
                    .message!! shouldBe
                    "workflow 'unregistered' is not registered with this engine — an instance it cannot look up again " +
                    "is an instance that cannot be resumed after a restart"
            }
        }
    })
