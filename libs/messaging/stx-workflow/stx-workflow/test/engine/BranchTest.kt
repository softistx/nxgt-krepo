package com.softistx.workflow.engine

import com.softistx.common.serialization.lenientJson
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.branch
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean

class BranchTest :
    FeatureSpec({
        fun forking(calls: Calls) =
            workflow<Ledger>("delivery") {
                branch("pick") {
                    on("express", { it.express }) {
                        step("fast") {
                            calls.record("fast")
                            context.copy(note = "fast")
                        }
                    }
                    otherwise {
                        step("slow") {
                            calls.record("slow")
                            context.copy(note = "slow")
                        }
                    }
                }
                step("after") {
                    calls.record("after")
                    context
                }
            }

        feature("choosing an arm") {
            scenario("the first arm whose condition holds runs, and the others do not") {
                val calls = Calls()
                val flow = forking(calls)
                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger(express = true))

                calls.all() shouldBe listOf("fast", "after")
                instance.context.note shouldBe "fast"
            }

            scenario("'otherwise' is what runs when nothing matched") {
                val calls = Calls()
                val flow = forking(calls)
                WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger(express = false))

                calls.all() shouldBe listOf("slow", "after")
            }

            scenario("a branch that matches nothing and has no 'otherwise' runs nothing, which is not a failure") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("optional") {
                        branch("pick") {
                            on("express", { it.express }) {
                                step("fast") {
                                    calls.record("fast")
                                    context
                                }
                            }
                        }
                        step("after") {
                            calls.record("after")
                            context
                        }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger(express = false))

                calls.all() shouldBe listOf("after")
                instance.status shouldBe WorkflowStatus.Completed
            }

            scenario("the arm is journaled") {
                val flow = forking(Calls())
                val record = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger(express = true)).record

                record.latest("pick")!!.value shouldBe JsonPrimitive("express")
            }
        }

        feature("a fork already taken") {
            scenario("a resume takes the recorded arm, even though the context now says otherwise") {
                val calls = Calls()
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()
                val interrupted = AtomicBoolean(false)

                val flow =
                    workflow<Ledger>("sticky") {
                        branch("pick") {
                            on("express", { it.express }) {
                                step("fast") {
                                    calls.record("fast")
                                    if (interrupted.compareAndSet(false, true)) {
                                        reached.complete(Unit)
                                        awaitCancellation()
                                    }
                                    context
                                }
                            }
                            otherwise {
                                step("slow") {
                                    calls.record("slow")
                                    context
                                }
                            }
                        }
                    }

                val engine = WorkflowEngine(store) { register(flow) }
                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running = scope.launch { engine.start(flow, Ledger(express = true), "sticky-1") }
                reached.await()
                running.cancelAndJoin()

                // Somebody — a later step in a longer workflow, or an operator — flips the field the
                // fork was decided on. The fork must not be re-decided.
                val stopped = store.load("sticky-1")!!
                store.save(
                    stopped.copy(context = lenientJson.encodeToJsonElement(Ledger.serializer(), Ledger(express = false))),
                    stopped.version,
                )

                calls.clear()
                val resumed = engine.resume(flow, "sticky-1")

                calls.all() shouldBe listOf("fast")
                resumed.status shouldBe WorkflowStatus.Completed
            }
        }
    })
