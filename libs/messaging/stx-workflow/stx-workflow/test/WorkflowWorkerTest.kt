package com.softistx.workflow

import com.softistx.workflow.dsl.sleep
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.store.WorkflowRecord
import com.softistx.workflow.store.WorkflowStore
import com.softistx.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The worker, against the store in this module.
 *
 * It knows nothing about Redis — it asks the engine what is due and resumes it, which is two
 * methods on `WorkflowStore` — so the specs that need a real server to say something (a process
 * killed mid-step, two engines on one instance) live in `stx-workflow-db`, and what belongs
 * here is that the loop itself works and that closing it stops it.
 */
class WorkflowWorkerTest :
    FeatureSpec({
        feature("the worker") {
            scenario("it wakes an instance whose pause is over, with nobody asking") {
                val calls = Calls()
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("napping") {
                        step("open") {
                            calls.record("open")
                            context
                        }
                        sleep("wait", 50.milliseconds)
                        step("close") {
                            calls.record("close")
                            context
                        }
                    }
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                store.load(id)!!.status shouldBe WorkflowStatus.Sleeping

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                WorkflowWorker(engine, poll = 20.milliseconds).use { worker ->
                    worker.start(scope)
                    withTimeout(10.seconds) {
                        while (store.load(id)!!.status != WorkflowStatus.Completed) delay(20)
                    }
                }
                scope.cancel()

                calls.all() shouldBe listOf("open", "close")
            }

            scenario("closing it stops the loop, and a later pause is left alone") {
                val store = InMemoryStore()
                val flow =
                    workflow<Ledger>("left-alone") {
                        step("open") { context }
                        sleep("wait", 50.milliseconds)
                        step("close") { context }
                    }
                val engine = WorkflowEngine(store) { register(flow) }
                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val worker = WorkflowWorker(engine, poll = 20.milliseconds)
                worker.start(scope)
                worker.close()

                val id = engine.start(flow, Ledger()).id
                delay(300)

                store.load(id)!!.status shouldBe WorkflowStatus.Sleeping
                scope.cancel()
            }

            scenario("a backlog larger than the concurrency never has more coroutines or handlers live than the concurrency") {
                val store = InMemoryStore()
                val running = AtomicInteger()
                val peak = AtomicInteger()
                val done = AtomicInteger()
                val gate = CompletableDeferred<Unit>()
                val flow =
                    workflow<Ledger>("backlog") {
                        step("work") {
                            peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                            gate.await()
                            running.decrementAndGet()
                            done.incrementAndGet()
                            context
                        }
                    }
                val engine = WorkflowEngine(store) { register(flow) }
                repeat(40) { index ->
                    val now = Clock.System.now()
                    store.create(
                        WorkflowRecord("w-$index", "backlog", WorkflowStatus.Running, JsonObject(emptyMap()), createdAt = now, updatedAt = now),
                    )
                }

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val worker = WorkflowWorker(engine, poll = 10.milliseconds, batch = 40, concurrency = 3)
                val job = worker.start(scope)
                delay(500)

                job.children.count() shouldBe 3
                peak.get() shouldBe 3
                gate.complete(Unit)
                withTimeout(10.seconds) { while (done.get() < 40) delay(20) }
                peak.get() shouldBe 3
                worker.close()
                scope.cancel()
            }

            scenario("an instance of a workflow nobody registered is left alone, not failed and not polled in a loop") {
                val inner = InMemoryStore()
                val runnableCalls = AtomicInteger()
                val store =
                    object : WorkflowStore by inner {
                        override suspend fun runnable(
                            now: Instant,
                            limit: Int,
                        ): List<String> {
                            runnableCalls.incrementAndGet()
                            return inner.runnable(now, limit)
                        }
                    }
                val now = Clock.System.now()
                store.create(
                    WorkflowRecord("ghost", "nobody-registered", WorkflowStatus.Running, JsonObject(emptyMap()), createdAt = now, updatedAt = now),
                )
                val engine = WorkflowEngine(store) { }
                val reported = AtomicInteger()

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val worker = WorkflowWorker(engine, poll = 50.milliseconds, onError = { _, _ -> reported.incrementAndGet() })
                worker.start(scope)
                delay(600)
                worker.close()
                scope.cancel()

                // One try, then the id is parked: the loop only polls, about every 50 ms.
                runnableCalls.get() shouldBeLessThan 30
                reported.get() shouldBe 1
                store.load("ghost")!!.status shouldBe WorkflowStatus.Running
            }
        }
    })
