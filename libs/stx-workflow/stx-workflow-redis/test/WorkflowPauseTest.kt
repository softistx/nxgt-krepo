package com.strange.workflow.redis

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.await
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.signal
import com.strange.workflow.dsl.sleep
import com.strange.workflow.dsl.step
import com.strange.workflow.redis.fixture.Approval
import com.strange.workflow.redis.fixture.Calls
import com.strange.workflow.redis.fixture.Ledger
import com.strange.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val APPROVAL = signal<Approval>("approval")

/**
 * An instance that stops, on a real store, and outlives the process it stopped in.
 *
 * This is the claim the whole feature rests on and the one an in-memory store cannot make: the
 * engine that parks the instance and the engine that finishes it are different objects, with
 * different registries, and nothing but Redis passes between them. A `delay` would have died with
 * the first one.
 */
class WorkflowPauseTest :
    FeatureSpec({
        feature("an instance waiting for a person").config(enabled = WorkflowTestServer.available) {
            scenario("it survives the process that parked it, and another one approves it") {
                WorkflowTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis)
                    val calls = Calls()
                    val flow =
                        workflow<Ledger>("refund") {
                            step("reserve") {
                                calls.record("reserve")
                                context.copy(reservationId = "r-1")
                            } compensate { calls.record("release") }
                            await(APPROVAL) { approval -> context.copy(approvedBy = approval.by) }
                            step("pay") {
                                calls.record("pay")
                                context.copy(chargeId = "c-1")
                            }
                        }

                    WorkflowEngine(store) { register(flow) }.start(flow, Ledger(), "wait-1")
                    store.load("wait-1")!!.status shouldBe WorkflowStatus.Awaiting

                    // A different engine entirely — the approval arrives at whichever process is up.
                    val approver = WorkflowEngine(store) { register(flow) }
                    val record = approver.signal("wait-1", APPROVAL, Approval(by = "ops"))

                    record.status shouldBe WorkflowStatus.Completed
                    calls.all() shouldBe listOf("reserve", "pay")
                    store
                        .load("wait-1")!!
                        .context
                        .toString()
                        .contains("ops") shouldBe true
                }
            }

            scenario("it is not in the due-time index, so no worker ever offers it to itself") {
                WorkflowTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis)
                    val flow =
                        workflow<Ledger>("open-ended") {
                            step("open") { context }
                            await(APPROVAL) { context }
                        }

                    WorkflowEngine(store) { register(flow) }.start(flow, Ledger(), "parked-1")

                    // Not now, and not in a week either: nothing but a signal moves this one.
                    store.runnable(Clock.System.now(), limit = 10).shouldBeEmpty()
                    store.runnable(Clock.System.now() + 7.days, limit = 10).shouldBeEmpty()
                }
            }
        }

        feature("an instance waiting for a clock").config(enabled = WorkflowTestServer.available) {
            scenario("a worker wakes it when it is due, and not before") {
                WorkflowTestServer.withRedis { redis ->
                    val store = RedisWorkflowStore(redis, lease = 200.milliseconds)
                    val calls = Calls()
                    val flow =
                        workflow<Ledger>("cool-off") {
                            step("open") {
                                calls.record("open")
                                context
                            }
                            sleep("wait", 300.milliseconds)
                            step("close") {
                                calls.record("close")
                                context.copy(note = "closed")
                            }
                        }

                    val engine = WorkflowEngine(store) { register(flow) }
                    engine.start(flow, Ledger(), "nap-1")
                    store.load("nap-1")!!.status shouldBe WorkflowStatus.Sleeping
                    calls.all() shouldBe listOf("open")

                    // Well past a lease, and still asleep — it is the wake-up time in the index that
                    // holds it, not the lock, which the process that parked it released long ago.
                    delay(100)
                    store.load("nap-1")!!.status shouldBe WorkflowStatus.Sleeping

                    val scope = CoroutineScope(Job() + Dispatchers.Default)
                    WorkflowWorker(engine, poll = 25.milliseconds).use { worker ->
                        worker.start(scope)
                        withTimeout(10.seconds) {
                            while (store.load("nap-1")!!.status != WorkflowStatus.Completed) delay(25)
                        }
                    }
                    scope.cancel()

                    calls.all() shouldBe listOf("open", "close")
                    store.load("nap-1")!!.wakeAt shouldBe null
                }
            }
        }
    })
