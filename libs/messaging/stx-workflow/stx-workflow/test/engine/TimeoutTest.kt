package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.exponential
import com.softistx.workflow.dsl.retry
import com.softistx.workflow.dsl.step
import com.softistx.workflow.dsl.timeout
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class TimeoutTest :
    FeatureSpec({
        feature("a step that runs long") {
            scenario("it fails when its timeout passes, and the workflow unwinds") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("slow") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate { calls.record("undo:reserve") }
                        step("hang") {
                            delay(2000.milliseconds)
                            context
                        } timeout 30.milliseconds
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                instance.status shouldBe WorkflowStatus.Compensated
                instance.error!!.node shouldBe "hang"
                calls.all() shouldBe listOf("undo:reserve")
            }

            scenario("the clock covers one attempt, so a retried step gets the full timeout each time") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("slow-retried") {
                        step("hang") {
                            calls.record("hang")
                            delay(2000.milliseconds)
                            context
                        } timeout 30.milliseconds retry {
                            times = 3
                            backoff = exponential(1.milliseconds)
                        }
                    }

                WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.count("hang") shouldBe 3
            }
        }
    })
