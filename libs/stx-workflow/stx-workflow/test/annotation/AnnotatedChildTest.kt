package com.softistx.workflow.annotation

import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.StepScope
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.workflow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * A class that delegates to another workflow.
 *
 * The child is named by a string, because an annotation cannot hold a `Workflow<D>` — so everything
 * a string can get wrong is checked when the parent is built, which is most of what is asserted
 * here. The other half is that the node it produces is the same node the DSL's `child` produces:
 * one engine, one set of rules, two front ends.
 */
@WorkflowDefinition("annotated-ordering")
private class Ordering(
    private val calls: Calls,
    private val failAfter: Boolean = false,
) {
    @Step(1)
    suspend fun StepScope<Ledger>.reserve(): Ledger {
        calls.record("reserve")
        return context.copy(reservationId = "r-1")
    }

    @Compensate("reserve")
    suspend fun StepScope<Ledger>.release() {
        calls.record("release")
    }

    @Child(2, workflow = "fulfilment", name = "fulfil")
    fun StepScope<Ledger>.startFulfilment(): Ledger = Ledger(express = context.express)

    @ChildResult("fulfil")
    suspend fun StepScope<Ledger>.fulfilled(done: Ledger): Ledger {
        calls.record("fulfilled")
        return context.copy(booking = done.booking)
    }

    @Step(3)
    suspend fun StepScope<Ledger>.confirm(): Ledger {
        calls.record("confirm")
        if (failAfter) error("the till is down")
        return context
    }
}

class AnnotatedChildTest :
    FeatureSpec({
        fun fulfilment(calls: Calls) =
            workflow<Ledger>("fulfilment") {
                step("book") {
                    calls.record("book")
                    context.copy(booking = "b-1")
                } compensate {
                    calls.record("unbook")
                }
            }

        feature("a class that delegates") {
            scenario("it produces the same node the DSL's child does") {
                val calls = Calls()
                val fulfil = fulfilment(calls)
                val parent = workflowOf<Ledger>(Ordering(calls), fulfil)
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(fulfil)
                        register(parent)
                    }

                val instance = engine.start(parent, Ledger(), id = "o-1")

                instance.status shouldBe WorkflowStatus.Completed
                instance.context.booking shouldBe "b-1"
                calls.all() shouldBe listOf("reserve", "book", "fulfilled", "confirm")
                engine.record("o-1/fulfil").shouldNotBeNull().parent shouldBe "o-1"
            }

            scenario("undoing the parent undoes the child, as it does in the DSL") {
                val calls = Calls()
                val fulfil = fulfilment(calls)
                val parent = workflowOf<Ledger>(Ordering(calls, failAfter = true), fulfil)
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(fulfil)
                        register(parent)
                    }

                val instance = engine.start(parent, Ledger(), id = "o-2")

                instance.status shouldBe WorkflowStatus.Compensated
                calls.all() shouldBe listOf("reserve", "book", "fulfilled", "confirm", "unbook", "release")
            }
        }

        feature("what the string can get wrong") {
            scenario("a child workflow nobody passed is refused, and the message says what was given") {
                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Ordering(Calls())) }

                failure.message.shouldNotBeNull() shouldContain "'fulfilment', which was not passed to workflowOf"
            }

            scenario("a @Child with no @ChildResult is refused") {
                @WorkflowDefinition("half")
                class Half {
                    @Child(1, workflow = "fulfilment")
                    fun StepScope<Ledger>.fulfil(): Ledger = Ledger()
                }

                val failure =
                    shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Half(), fulfilment(Calls())) }

                failure.message.shouldNotBeNull() shouldContain "has no @ChildResult"
            }

            scenario("a @ChildResult naming no @Child is refused") {
                @WorkflowDefinition("stray")
                class Stray {
                    @Step(1)
                    suspend fun StepScope<Ledger>.one(): Ledger = context

                    @ChildResult("nowhere")
                    suspend fun StepScope<Ledger>.folded(done: Ledger): Ledger = context
                }

                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Stray()) }

                failure.message.shouldNotBeNull() shouldContain "@ChildResult(\"nowhere\") names no @Child"
            }

            scenario("a starting context of the wrong type is refused, naming both") {
                @WorkflowDefinition("mistyped")
                class Mistyped {
                    @Child(1, workflow = "fulfilment", name = "fulfil")
                    fun StepScope<Ledger>.start(): String = "not a Ledger"

                    @ChildResult("fulfil")
                    suspend fun StepScope<Ledger>.folded(done: Ledger): Ledger = context
                }

                val failure =
                    shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Mistyped(), fulfilment(Calls())) }

                failure.message.shouldNotBeNull() shouldContain "but the context of 'fulfilment' is"
            }

            scenario("a @Child that suspends is refused; it computes a context and nothing else") {
                @WorkflowDefinition("suspending")
                class Suspending {
                    @Child(1, workflow = "fulfilment", name = "fulfil")
                    suspend fun StepScope<Ledger>.start(): Ledger = Ledger()

                    @ChildResult("fulfil")
                    suspend fun StepScope<Ledger>.folded(done: Ledger): Ledger = context
                }

                val failure =
                    shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Suspending(), fulfilment(Calls())) }

                failure.message.shouldNotBeNull() shouldContain "must not suspend"
            }
        }
    })
