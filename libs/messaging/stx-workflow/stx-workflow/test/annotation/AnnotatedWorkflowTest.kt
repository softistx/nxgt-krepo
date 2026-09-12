package com.softistx.workflow.annotation

import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.StepScope
import com.softistx.workflow.dsl.signal
import com.softistx.workflow.fixture.Approval
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.fixture.Wobble
import com.softistx.workflow.store.InMemoryStore
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private val APPROVAL = signal<Approval>("approval")

/**
 * A workflow the annotations declare, written the way an application would.
 *
 * The steps are deliberately **out of source order**: `confirm` is declared before `charge`, and
 * only `@Step(order)` says otherwise. That is the point of the number — see its KDoc — and a spec
 * that declared them in order would prove nothing about it.
 */
@WorkflowDefinition("annotated-checkout")
private class Checkout(
    private val calls: Calls,
    private val failAt: String? = null,
) {
    @Step(3)
    suspend fun StepScope<Ledger>.confirm(): Ledger {
        calls.record("confirm")
        return context.copy(note = "confirmed")
    }

    @Step(1)
    suspend fun StepScope<Ledger>.reserve(): Ledger {
        calls.record("reserve")
        return context.copy(reservationId = "r-1")
    }

    @Compensate("reserve")
    suspend fun StepScope<Ledger>.releaseReservation() {
        calls.record("release")
    }

    @Step(2, name = "charge")
    @Retry(times = 3, delayMillis = 1)
    suspend fun StepScope<Ledger>.chargeCard(): Ledger {
        calls.record("charge")
        if (failAt == "charge") throw Wobble()
        return context.copy(chargeId = "c-$attempt")
    }

    @Compensate("charge")
    suspend fun StepScope<Ledger>.refund() {
        calls.record("refund")
    }
}

@WorkflowDefinition("annotated-refund")
private class Refund(
    private val calls: Calls,
) {
    @Step(1)
    suspend fun StepScope<Ledger>.hold(): Ledger {
        calls.record("hold")
        return context.copy(reservationId = "h-1")
    }

    @Await(2, signal = "approval", name = "approval")
    suspend fun StepScope<Ledger>.approved(approval: Approval): Ledger {
        calls.record("approved")
        return context.copy(approvedBy = approval.by)
    }

    @Sleep(3, name = "settle")
    fun StepScope<Ledger>.settlement(): Duration = if (context.waits == 0) Duration.ZERO else context.waits.days

    @Step(4)
    suspend fun StepScope<Ledger>.pay(): Ledger {
        calls.record("pay")
        return context.copy(chargeId = "p-1")
    }
}

class AnnotatedWorkflowTest :
    FeatureSpec({
        feature("an annotated class") {
            scenario("its steps run in the order the annotations give, not the order they are written") {
                val calls = Calls()
                val flow = workflowOf<Ledger>(Checkout(calls))
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val instance = engine.start(flow, Ledger())

                instance.status shouldBe WorkflowStatus.Completed
                calls.all() shouldBe listOf("reserve", "charge", "confirm")
                instance.context.note shouldBe "confirmed"
            }

            scenario("it is an ordinary Workflow — the name and the journal are the store's, not a parallel world") {
                val flow = workflowOf<Ledger>(Checkout(Calls()))
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val record = engine.start(flow, Ledger()).record

                flow.name shouldBe "annotated-checkout"
                record.journal.map { it.node } shouldBe listOf("reserve", "charge", "confirm")
            }

            scenario("@Compensate undoes its step, newest first") {
                val calls = Calls()
                val flow = workflowOf<Ledger>(Checkout(calls, failAt = "charge"))
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val instance = engine.start(flow, Ledger())

                instance.status shouldBe WorkflowStatus.Compensated
                // Three attempts at the charge, then the reservation released. Nothing to refund:
                // the charge never succeeded, so it has nothing recorded to undo.
                calls.all() shouldBe listOf("reserve", "charge", "charge", "charge", "release")
            }

            scenario("@Retry is the policy the engine uses, attempt count and all") {
                val calls = Calls()
                val flow = workflowOf<Ledger>(Checkout(calls))
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                engine.start(flow, Ledger()).context.chargeId shouldBe "c-1"
                calls.count("charge") shouldBe 1
            }
        }

        feature("waiting, declared by annotation") {
            scenario("@Await parks the instance, and the signal folds its payload in") {
                val calls = Calls()
                val flow = workflowOf<Ledger>(Refund(calls))
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val started = engine.start(flow, Ledger())

                started.status shouldBe WorkflowStatus.Awaiting
                started.record.awaiting shouldBe "approval"

                val record = engine.signal(started.id, APPROVAL, Approval(by = "ops"))

                record.status shouldBe WorkflowStatus.Completed
                calls.all() shouldBe listOf("hold", "approved", "pay")
            }

            scenario("@Sleep computes its duration from the context, and parks when it is positive") {
                val flow = workflowOf<Ledger>(Refund(Calls()))
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val started = engine.start(flow, Ledger(waits = 2))

                engine.signal(started.id, APPROVAL, Approval(by = "ops")).status shouldBe WorkflowStatus.Sleeping
            }
        }
    })
