package com.strange.workflow.redis

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.step
import com.strange.workflow.redis.fixture.Calls
import com.strange.workflow.redis.fixture.Ledger
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
import kotlin.time.Duration.Companion.milliseconds

class RedisResumeTest :
    FeatureSpec({
        feature("an instance a process abandoned").config(enabled = RedisTestServer.available) {
            scenario("a second engine picks it up where the first one stopped") {
                RedisTestServer.withRedis { redis ->
                    val calls = Calls()
                    val store = RedisWorkflowStore(redis, lease = 200.milliseconds)
                    val reached = CompletableDeferred<Unit>()
                    val interrupted = AtomicBoolean(false)

                    val flow =
                        workflow<Ledger>("resumable") {
                            step("reserve") {
                                calls.record("reserve")
                                context.copy(reservationId = "r-1")
                            }
                            step("charge") {
                                calls.record("charge")
                                if (interrupted.compareAndSet(false, true)) {
                                    reached.complete(Unit)
                                    awaitCancellation()
                                }
                                context.copy(chargeId = "c-1")
                            }
                            step("confirm") {
                                calls.record("confirm")
                                context.copy(note = "done")
                            }
                        }

                    // The process that dies.
                    val first = WorkflowEngine(store) { register(flow) }
                    val scope = CoroutineScope(Job() + Dispatchers.Default)
                    val running = scope.launch { first.start(flow, Ledger(), "run-1") }
                    reached.await()
                    running.cancelAndJoin()

                    store.load("run-1")!!.status shouldBe WorkflowStatus.Running
                    store.load("run-1")!!.journal.map { it.node } shouldBe listOf("reserve")

                    // A second process, holding nothing of the first one's memory. The abandoned
                    // lock is not released by a cancelled coroutine — it is left to expire, which is
                    // what the lease is for.
                    calls.clear()
                    val second = WorkflowEngine(RedisWorkflowStore(redis, lease = 200.milliseconds)) { register(flow) }
                    kotlinx.coroutines.delay(300.milliseconds)
                    val instance = second.resume(flow, "run-1")

                    calls.all() shouldBe listOf("charge", "confirm")
                    instance.status shouldBe WorkflowStatus.Completed
                    instance.context shouldBe Ledger("r-1", "c-1", "done")
                }
            }

            scenario("a compensation half done is not done twice") {
                RedisTestServer.withRedis { redis ->
                    val calls = Calls()
                    val store = RedisWorkflowStore(redis, lease = 200.milliseconds)
                    val reached = CompletableDeferred<Unit>()
                    val interrupted = AtomicBoolean(false)

                    val flow =
                        workflow<Ledger>("half-unwound") {
                            step("reserve") { context.copy(reservationId = "r-1") } compensate
                                {
                                    calls.record("undo:reserve")
                                    if (interrupted.compareAndSet(false, true)) {
                                        reached.complete(Unit)
                                        awaitCancellation()
                                    }
                                }
                            step("charge") { context.copy(chargeId = "c-1") } compensate { calls.record("undo:charge") }
                            step("ship") { error("no courier") }
                        }

                    val engine = WorkflowEngine(store) { register(flow) }
                    val scope = CoroutineScope(Job() + Dispatchers.Default)
                    val running = scope.launch { engine.start(flow, Ledger(), "unwind-1") }
                    reached.await()
                    running.cancelAndJoin()

                    // 'charge' was already undone and recorded; 'reserve' was interrupted mid-undo.
                    calls.all() shouldBe listOf("undo:charge", "undo:reserve")

                    calls.clear()
                    kotlinx.coroutines.delay(300.milliseconds)
                    val instance = WorkflowEngine(store) { register(flow) }.resume(flow, "unwind-1")

                    calls.all() shouldBe listOf("undo:reserve")
                    instance.status shouldBe WorkflowStatus.Compensated
                }
            }
        }
    })
