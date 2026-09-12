package com.softistx.workflow.engine

import com.softistx.workflow.NonRetryableException
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.exponential
import com.softistx.workflow.dsl.retry
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.fixture.Wobble
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class RetryTest :
    FeatureSpec({
        feature("retrying a step") {
            scenario("it stops as soon as an attempt works, and the journal counts them") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("flaky") {
                        step("charge") {
                            calls.record("charge")
                            if (attempt < 3) throw Wobble()
                            context.copy(chargeId = "c-$attempt")
                        } retry {
                            times = 5
                            backoff = exponential(1.milliseconds, max = 5.milliseconds)
                        }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.count("charge") shouldBe 3
                instance.context.chargeId shouldBe "c-3"
                instance.record.latest("charge")!!.attempts shouldBe 3
            }

            scenario("without a retry a step is tried once") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("brittle") {
                        step("charge") {
                            calls.record("charge")
                            throw Wobble()
                        }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.count("charge") shouldBe 1
                instance.status shouldBe WorkflowStatus.Compensated
                instance.error!!.attempts shouldBe 1
            }

            scenario("a NonRetryableException ends the node however many attempts were left") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("declined") {
                        step("charge") {
                            calls.record("charge")
                            throw NonRetryableException("insufficient funds")
                        } retry {
                            times = 4
                            backoff = exponential(1.milliseconds)
                        }
                    }

                val instance = WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.count("charge") shouldBe 1
                instance.error!!.type shouldBe "com.softistx.workflow.NonRetryableException"
            }

            scenario("'unless' says which failures the policy itself considers final") {
                val calls = Calls()
                val flow =
                    workflow<Ledger>("selective") {
                        step("charge") {
                            calls.record("charge")
                            throw Wobble()
                        } retry {
                            times = 4
                            backoff = exponential(1.milliseconds)
                            unless { it is Wobble }
                        }
                    }

                WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, Ledger())

                calls.count("charge") shouldBe 1
            }
        }
    })
