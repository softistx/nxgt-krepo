package com.strange.workflow.engine

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.exponential
import com.strange.workflow.dsl.retry
import com.strange.workflow.dsl.step
import com.strange.workflow.dsl.timeout
import com.strange.workflow.fixture.Calls
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
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
