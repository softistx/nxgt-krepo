package com.strange.workflow.engine

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.step
import com.strange.workflow.fixture.Calls
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A crash, without a crash.
 *
 * Cancelling the scope a run is on leaves exactly what killing the process leaves: the checkpoint
 * for the last completed node is on disk, the interrupted node's is not, and nothing was marked
 * failed — because the engine rethrows a `CancellationException` untouched rather than treating it
 * as a step that failed. An instance interrupted this way is `Running` with a journal one node
 * short, which is what a resume has to cope with.
 */
class ResumeTest :
    FeatureSpec({
        feature("an instance nobody finished") {
            scenario("it restarts at the interrupted node and replays nothing before it") {
                val calls = Calls()
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()
                val interrupted = AtomicBoolean(false)

                val flow =
                    workflow<Ledger>("resumable") {
                        step("one") {
                            calls.record("one")
                            context.copy(reservationId = "r-1")
                        }
                        step("two") {
                            calls.record("two")
                            if (interrupted.compareAndSet(false, true)) {
                                reached.complete(Unit)
                                awaitCancellation()
                            }
                            context.copy(chargeId = "c-1")
                        }
                        step("three") {
                            calls.record("three")
                            context.copy(note = "done")
                        }
                    }

                val engine = WorkflowEngine(store) { register(flow) }
                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running = scope.launch { engine.start(flow, Ledger(), "run-1") }
                reached.await()
                running.cancelAndJoin()

                val stopped = store.load("run-1")!!
                stopped.status shouldBe WorkflowStatus.Running
                stopped.journal.map { it.node } shouldBe listOf("one")
                calls.all() shouldBe listOf("one", "two")

                // A second engine, as a restarted process would be.
                calls.clear()
                val recovered = WorkflowEngine(store) { register(flow) }
                val instance = recovered.resume(flow, "run-1")

                // 'two' runs again — the checkpoint is written after the effect, so delivery is
                // at-least-once and a step has to be idempotent. 'one' does not.
                calls.all() shouldBe listOf("two", "three")
                instance.status shouldBe WorkflowStatus.Completed
                instance.context shouldBe Ledger(reservationId = "r-1", chargeId = "c-1", note = "done")
            }

            scenario("resuming one that already finished changes nothing") {
                val calls = Calls()
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("short") {
                        step("only") {
                            calls.record("only")
                            context
                        }
                    }

                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                engine.resume(flow, id).status shouldBe WorkflowStatus.Completed
                calls.count("only") shouldBe 1
            }

            scenario("the instance's lock is released when the run is cancelled, not held forever") {
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()
                val flow =
                    workflow<Ledger>("held") {
                        step("hang") {
                            reached.complete(Unit)
                            awaitCancellation()
                        }
                    }

                val engine = WorkflowEngine(store) { register(flow) }
                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running = scope.launch { engine.start(flow, Ledger(), "held-1") }
                reached.await()
                running.cancelAndJoin()

                store.guarded("held-1") { "free" } shouldBe "free"
            }
        }
    })
