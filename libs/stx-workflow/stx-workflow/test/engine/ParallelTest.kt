package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.exponential
import com.softistx.workflow.dsl.outcome
import com.softistx.workflow.dsl.parallel
import com.softistx.workflow.dsl.retry
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.fixture.Wobble
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val CHARGE = outcome<String>("charge")
private val COURIER = outcome<String>("courier")

class ParallelTest :
    FeatureSpec({
        feature("a fan-out") {
            scenario("its legs really do overlap") {
                // Each leg waits for the other to have started. If they ran one after the other this
                // deadlocks, which is a stronger statement than any duration would be.
                val first = CompletableDeferred<Unit>()
                val second = CompletableDeferred<Unit>()

                val flow =
                    workflow<Ledger>("concurrent") {
                        parallel("provision") {
                            branch(CHARGE) {
                                first.complete(Unit)
                                second.await()
                                "c-1"
                            }
                            branch(COURIER) {
                                second.complete(Unit)
                                first.await()
                                "b-1"
                            }
                            merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
                        }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                instance.context shouldBe Ledger(chargeId = "c-1", booking = "b-1")
                instance.status shouldBe WorkflowStatus.Completed
            }

            scenario("each leg is journaled under the fan-out's name") {
                val flow =
                    workflow<Ledger>("named") {
                        parallel("provision") {
                            branch(CHARGE) { "c-1" }
                            branch(COURIER) { "b-1" }
                            merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
                        }
                    }

                val record = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger()).record

                record.journal.map { it.node }.toSet() shouldBe setOf("provision/charge", "provision/courier", "provision")
            }
        }

        feature("a leg that fails") {
            scenario("the legs that succeeded are undone, and then so are the steps before the fan-out") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("half-provisioned") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate { calls.record("undo:reserve") }
                        parallel("provision") {
                            branch(CHARGE) { "c-1" } compensate { id -> calls.record("undo:charge:$id") }
                            branch(COURIER) { throw Wobble("no courier") } compensate { calls.record("undo:courier") }
                            merge { out -> context.copy(chargeId = out[CHARGE]) }
                        }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.all() shouldBe listOf("undo:charge:c-1", "undo:reserve")
                instance.status shouldBe WorkflowStatus.Compensated
                instance.error!!.node shouldBe "provision/courier"
            }

            scenario("a retry on the fan-out re-runs only what has not succeeded") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("retried-fanout") {
                        parallel("provision") {
                            branch(CHARGE) {
                                calls.record("charge")
                                "c-1"
                            }
                            branch(COURIER) {
                                calls.record("courier")
                                if (calls.count("courier") < 3) throw Wobble()
                                "b-1"
                            }
                            merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
                        } retry {
                            times = 4
                            backoff = exponential(1.milliseconds)
                        }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.count("charge") shouldBe 1
                calls.count("courier") shouldBe 3
                instance.context shouldBe Ledger(chargeId = "c-1", booking = "b-1")
            }
        }

        feature("a fan-out nobody finished") {
            scenario("a resume runs only the leg that had not landed") {
                val calls = Calls()
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()
                val hang = CompletableDeferred<Unit>()

                val flow =
                    workflow<Ledger>("interrupted-fanout") {
                        parallel("provision") {
                            branch(CHARGE) {
                                calls.record("charge")
                                "c-1"
                            }
                            branch(COURIER) {
                                calls.record("courier")
                                reached.complete(Unit)
                                hang.await()
                                "b-1"
                            }
                            merge { out -> context.copy(chargeId = out[CHARGE], booking = out[COURIER]) }
                        }
                    }

                val engine = WorkflowEngine(store) { register(flow) }
                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running = scope.launch { engine.start(flow, Ledger(), "fan-1") }

                reached.await()
                while (store.load("fan-1")?.succeeded("provision/charge") == null) delay(5)
                running.cancelAndJoin()

                calls.clear()
                hang.complete(Unit)
                val instance = engine.resume(flow, "fan-1")

                calls.all() shouldBe listOf("courier")
                instance.context shouldBe Ledger(chargeId = "c-1", booking = "b-1")
                instance.status shouldBe WorkflowStatus.Completed
            }
        }
    })
