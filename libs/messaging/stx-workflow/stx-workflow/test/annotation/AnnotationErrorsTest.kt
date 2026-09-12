package com.softistx.workflow.annotation

import com.softistx.workflow.dsl.StepScope
import com.softistx.workflow.fixture.Approval
import com.softistx.workflow.fixture.Ledger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration

private class NotAnnotated

@WorkflowDefinition("empty")
private class Empty

@WorkflowDefinition("mistaken")
private class Mistaken {
    @Step(1)
    suspend fun StepScope<Ledger>.first(): Ledger = context

    @Step(1)
    suspend fun StepScope<Ledger>.second(): Ledger = context

    @Compensate("nobody")
    suspend fun StepScope<Ledger>.undoNobody() = Unit
}

@WorkflowDefinition("wrong-context")
private class WrongContext {
    @Step(1)
    suspend fun StepScope<String>.only(): String = context
}

@WorkflowDefinition("wrong-shapes")
private class WrongShapes {
    @Step(1)
    suspend fun StepScope<Ledger>.returnsNothing() = Unit

    @Await(2, signal = "approval")
    suspend fun StepScope<Ledger>.noPayload(): Ledger = context

    @Sleep(3)
    suspend fun StepScope<Ledger>.suspendingPause(): Duration = Duration.ZERO

    @Step(4)
    @Await(5, signal = "other")
    suspend fun StepScope<Ledger>.bothAtOnce(approval: Approval): Ledger = context
}

/**
 * What a class gets wrong, and when it is told.
 *
 * All of it at startup, and **all of it at once**. A reader that stopped at the first problem would
 * make a class with three mistakes take three runs to fix, and this front end's mistakes come in
 * groups: a context type restated wrongly in one place is usually restated wrongly in four.
 */
class AnnotationErrorsTest :
    FeatureSpec({
        feature("a class that is not a workflow") {
            scenario("one with no @WorkflowDefinition says so by name") {
                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(NotAnnotated()) }

                failure.message shouldContain "is not annotated @WorkflowDefinition"
            }

            scenario("one with the annotation and no nodes is refused too") {
                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Empty()) }

                failure.message shouldContain "it declares no @Step, @Await, @Sleep or @Child"
            }
        }

        feature("a class with several mistakes") {
            scenario("every one of them is in the message") {
                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Mistaken()) }

                failure.message shouldContain "two nodes claim order 1"
                failure.message shouldContain "@Compensate(\"nobody\") names no @Step"
            }

            scenario("a context that is not this workflow's names both types") {
                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(WrongContext()) }

                failure.message shouldContain "StepScope<kotlin.String>"
                failure.message shouldContain "com.softistx.workflow.fixture.Ledger"
            }

            scenario("a node of the wrong shape is named, whichever shape it got wrong") {
                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(WrongShapes()) }
                val message = failure.message!!

                message shouldContain "returnsNothing returns kotlin.Unit"
                message shouldContain "noPayload is an @Await and must take exactly one parameter"
                message shouldContain "suspendingPause is a @Sleep and must not suspend"
                message shouldContain "bothAtOnce is annotated as more than one kind of node"
            }
        }

        feature("a mistake the DSL would also catch") {
            scenario("it is caught the same way, because this builds the same workflow") {
                @WorkflowDefinition("twice")
                class Twice {
                    @Step(1, name = "same")
                    suspend fun StepScope<Ledger>.one(): Ledger = context

                    @Step(2, name = "same")
                    suspend fun StepScope<Ledger>.two(): Ledger = context
                }

                val failure = shouldThrow<IllegalArgumentException> { workflowOf<Ledger>(Twice()) }

                failure.message shouldBe "workflow 'twice' declares 'same' twice"
            }
        }
    })
