package com.strange.workflow.redis

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.WorkflowWorker
import com.strange.workflow.dsl.step
import com.strange.workflow.redis.fixture.Ledger
import com.strange.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WorkflowWorkerTest :
    FeatureSpec({
        feature("the worker").config(enabled = WorkflowTestServer.available) {
            scenario("it finishes an instance whose process died, without anybody asking") {
                WorkflowTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis, lease = 200.milliseconds)
                    val reached = CompletableDeferred<Unit>()
                    val interrupted = AtomicBoolean(false)

                    val flow =
                        workflow<Ledger>("abandoned") {
                            step("reserve") { context.copy(reservationId = "r-1") }
                            step("charge") {
                                if (interrupted.compareAndSet(false, true)) {
                                    reached.complete(Unit)
                                    awaitCancellation()
                                }
                                context.copy(chargeId = "c-1")
                            }
                        }

                    val dying = WorkflowEngine(store) { register(flow) }
                    val scope = CoroutineScope(Job() + Dispatchers.Default)
                    val running = scope.launch { dying.start(flow, Ledger(), "orphan-1") }
                    reached.await()
                    running.cancelAndJoin()
                    store.load("orphan-1")!!.status shouldBe WorkflowStatus.Running

                    val engine = WorkflowEngine(store) { register(flow) }
                    val workerScope = CoroutineScope(Job() + Dispatchers.Default)
                    WorkflowWorker(engine, poll = 50.milliseconds).use { worker ->
                        worker.start(workerScope)
                        withTimeout(10.seconds) {
                            while (store.load("orphan-1")!!.status != WorkflowStatus.Completed) delay(25)
                        }
                    }
                    workerScope.cancel()

                    store.load("orphan-1")!!.status shouldBe WorkflowStatus.Completed
                }
            }

            scenario("it leaves alone an instance somebody is advancing right now") {
                WorkflowTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis, lease = 30.seconds)
                    val inside = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    var entries = 0

                    val flow =
                        workflow<Ledger>("contended") {
                            step("hold") {
                                entries++
                                inside.complete(Unit)
                                release.await()
                                context.copy(note = "held")
                            }
                        }

                    val holder = WorkflowEngine(store) { register(flow) }
                    val scope = CoroutineScope(Job() + Dispatchers.Default)
                    val running = scope.launch { holder.start(flow, Ledger(), "busy-1") }
                    inside.await()

                    // Another engine asking for the same instance is told no, not queued behind it.
                    val other = WorkflowEngine(store) { register(flow) }
                    other.resume("busy-1").status shouldBe WorkflowStatus.Running
                    entries shouldBe 1

                    release.complete(Unit)
                    running.join()
                    store.load("busy-1")!!.status shouldBe WorkflowStatus.Completed
                    entries shouldBe 1
                }
            }
        }
    })
