package com.strange.workflow.engine

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.exponential
import com.strange.workflow.dsl.outcome
import com.strange.workflow.dsl.parallel
import com.strange.workflow.dsl.retry
import com.strange.workflow.dsl.step
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.fixture.Wobble
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * The key a step gives its remote call is the only thing standing between at-least-once delivery and
 * a card charged twice, so the three properties it rests on are asserted rather than assumed: it does
 * not change when the node is retried, it does not change when the instance is resumed in another
 * process, and it is not the same for any two nodes.
 */
class IdempotencyTest :
    FeatureSpec({
        feature("the key a node hands its remote call") {
            scenario("it is the same on every attempt of one run") {
                val keys = mutableListOf<String>()
                val flow =
                    workflow<Ledger>("retried") {
                        step("charge") {
                            keys += idempotencyKey
                            if (attempt < 3) throw Wobble()
                            context
                        } retry {
                            times = 3
                            backoff = exponential(1.milliseconds)
                        }
                    }

                WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger(), "order-7")

                keys shouldHaveSize 3
                keys.toSet() shouldBe setOf("retried:order-7:charge")
            }

            scenario("it survives a crash: the replayed node presents the key it presented before") {
                val keys = mutableListOf<String>()
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()
                val interrupted = AtomicBoolean(false)

                val flow =
                    workflow<Ledger>("resumed") {
                        step("charge") {
                            keys += idempotencyKey
                            if (interrupted.compareAndSet(false, true)) {
                                reached.complete(Unit)
                                awaitCancellation()
                            }
                            context.copy(chargeId = "c-1")
                        }
                    }

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running =
                    scope.launch { WorkflowEngine(store) { register(flow) }.start(flow, Ledger(), "order-7") }
                reached.await()
                running.cancelAndJoin()

                // A different engine, as a restarted process would be.
                val instance = WorkflowEngine(store) { register(flow) }.resume(flow, "order-7")

                instance.status shouldBe WorkflowStatus.Completed
                keys shouldHaveSize 2
                keys.toSet() shouldBe setOf("resumed:order-7:charge")
            }

            scenario("no two nodes share one, legs of a fan-out included") {
                val keys = mutableListOf<String>()
                val charge = outcome<String>("charge")
                val courier = outcome<String>("courier")

                val flow =
                    workflow<Ledger>("distinct") {
                        step("reserve") {
                            keys += idempotencyKey
                            context
                        }
                        parallel("provision") {
                            branch(charge) {
                                keys += idempotencyKey
                                "c-1"
                            }
                            branch(courier) {
                                keys += idempotencyKey
                                "b-1"
                            }
                            merge { out -> context.copy(chargeId = out[charge], note = out[courier]) }
                        }
                    }

                WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger(), "order-7")

                keys.toSet() shouldBe
                    setOf(
                        "distinct:order-7:reserve",
                        "distinct:order-7:provision/charge",
                        "distinct:order-7:provision/courier",
                    )
            }

            scenario("two instances of one workflow do not share one") {
                val keys = mutableListOf<String>()
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("twice") {
                        step("charge") {
                            keys += idempotencyKey
                            context
                        }
                    }
                val engine = WorkflowEngine(store) { register(flow) }

                engine.start(flow, Ledger(), "order-1")
                engine.start(flow, Ledger(), "order-2")

                keys shouldBe listOf("twice:order-1:charge", "twice:order-2:charge")
            }

            scenario("a node making two calls discriminates them, because one key would deduplicate the second away") {
                val keys = mutableListOf<String>()
                val flow =
                    workflow<Ledger>("two-calls") {
                        step("settle") {
                            keys += idempotencyKey("charge")
                            keys += idempotencyKey("tip")
                            context
                        }
                    }

                WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger(), "order-7")

                keys shouldBe listOf("two-calls:order-7:settle:charge", "two-calls:order-7:settle:tip")
            }
        }
    })
