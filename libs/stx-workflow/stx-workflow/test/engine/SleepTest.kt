package com.strange.workflow.engine

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.sleep
import com.strange.workflow.dsl.step
import com.strange.workflow.fixture.Calls
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Waiting for a clock.
 *
 * The distinction this file exists to hold is `sleep` against `delay`. A `delay` is a coroutine
 * parked in a process; a seven-day cool-off written that way is a seven-day uptime requirement, and
 * the deploy on Thursday loses every instance mid-wait. A `sleep` is a row with a due time — the
 * process it started in is free to die, and the one that finds it later finishes it.
 */
class SleepTest :
    FeatureSpec({
        fun pausing(
            calls: Calls,
            duration: Duration,
        ) = workflow<Ledger>("cool-off") {
            step("open") {
                calls.record("open")
                context
            }
            sleep("wait", duration)
            step("close") {
                calls.record("close")
                context
            }
        }

        feature("reaching a sleep") {
            scenario("it parks with the moment it is due, and runs nothing after it") {
                val calls = Calls()
                val flow = pausing(calls, 1.days)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val instance = engine.start(flow, Ledger())

                instance.status shouldBe WorkflowStatus.Sleeping
                instance.record.wakeAt!! shouldBeGreaterThan Clock.System.now()
                calls.all() shouldBe listOf("open")
            }

            scenario("nothing offers it until it is due, and then it does") {
                val store = InMemoryStore()
                val flow = pausing(Calls(), 1.days)
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                store.runnable(Clock.System.now(), limit = 10).shouldBeEmpty()
                store.runnable(Clock.System.now() + 2.days, limit = 10) shouldBe listOf(id)
            }

            scenario("resuming it early leaves it asleep, at the same moment it already had") {
                val calls = Calls()
                val flow = pausing(calls, 1.days)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val started = engine.start(flow, Ledger())
                calls.clear()

                val resumed = engine.resume(started.id)

                resumed.status shouldBe WorkflowStatus.Sleeping
                resumed.wakeAt shouldBe started.record.wakeAt
                calls.all().shouldBeEmpty()
            }
        }

        feature("waking up") {
            scenario("once the moment has passed it goes on to the end") {
                val calls = Calls()
                val flow = pausing(calls, 20.milliseconds)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                delay(50)
                val record = engine.resume(id)

                record.status shouldBe WorkflowStatus.Completed
                record.wakeAt shouldBe null
                calls.all() shouldBe listOf("open", "close")
            }
        }

        feature("a computed pause") {
            scenario("it reads the context") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("backing-off") {
                        sleep("wait") { context.waits.seconds }
                        step("done") {
                            calls.record("done")
                            context
                        }
                    }
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                engine.start(flow, Ledger(waits = 3600)).status shouldBe WorkflowStatus.Sleeping
            }

            scenario("it is asked once, so restarts do not push the wake-up back") {
                var asked = 0
                val flow =
                    workflow<Ledger>("asked-once") {
                        sleep("wait") {
                            asked++
                            1.days
                        }
                        step("done") { context }
                    }
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val started = engine.start(flow, Ledger())

                engine.resume(started.id)
                engine.resume(started.id)

                asked shouldBe 1
                engine.record(started.id)?.wakeAt shouldBe started.record.wakeAt
            }

            scenario("a pause already over is not a pause at all") {
                val calls = Calls()
                val flow = pausing(calls, Duration.ZERO)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val instance = engine.start(flow, Ledger())

                instance.status shouldBe WorkflowStatus.Completed
                calls.all() shouldBe listOf("open", "close")
            }
        }
    })
