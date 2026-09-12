package com.softistx.workflow

import com.softistx.workflow.dsl.sleep
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
        }
    })
