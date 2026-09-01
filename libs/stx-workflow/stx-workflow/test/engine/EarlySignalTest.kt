package com.strange.workflow.engine

import com.strange.workflow.WorkflowConflictException
import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.WorkflowUnknownSignalException
import com.strange.workflow.dsl.await
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.signal
import com.strange.workflow.dsl.sleep
import com.strange.workflow.dsl.step
import com.strange.workflow.fixture.Approval
import com.strange.workflow.fixture.Calls
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val APPROVAL = signal<Approval>("approval")
private val DISPATCH = signal<Approval>("dispatch")

/**
 * A signal that arrives before the wait it answers.
 *
 * This is not an exotic case. A payment provider handed an idempotency key and a callback URL will
 * often have called back before the step that asked it to has returned, and a workflow that waits on
 * two things in sequence hears about the second while it is still parked on the first. Neither is a
 * caller doing something wrong, so neither may cost the payload.
 *
 * What makes that safe is that a delivery is durable the moment it is accepted, filed under the name
 * of the signal it belongs to. The engine never has to decide whether a payload is early or late —
 * the wait reads the one addressed to it, whenever it gets there.
 */
class EarlySignalTest :
    FeatureSpec({
        fun shipping(
            calls: Calls,
            coolOff: Duration? = null,
        ) = workflow<Ledger>("shipping") {
            step("reserve") {
                calls.record("reserve")
                context.copy(reservationId = "r-1")
            } compensate {
                calls.record("release")
            }

            coolOff?.let { sleep("cool-off", it) }

            await(APPROVAL) { approval ->
                calls.record("approved")
                context.copy(approvedBy = approval.by)
            }

            step("charge") {
                calls.record("charge")
                context.copy(chargeId = "c-1")
            }

            await(DISPATCH) { dispatch ->
                calls.record("dispatched")
                context.copy(booking = dispatch.by)
            }
        }

        feature("a payload that arrives before its wait") {
            scenario("it is kept, and read when the run reaches the wait it belongs to") {
                val calls = Calls()
                val flow = shipping(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                engine.signal(id, DISPATCH, Approval(by = "warehouse"))
                val record = engine.signal(id, APPROVAL, Approval(by = "ops"))

                record.status shouldBe WorkflowStatus.Completed
                calls.all() shouldBe listOf("reserve", "approved", "charge", "dispatched")
            }

            scenario("it does not step over the wait the instance is actually parked on") {
                val calls = Calls()
                val flow = shipping(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                val record = engine.signal(id, DISPATCH, Approval(by = "warehouse"))

                // Still waiting for the approval, and nothing after it has run. The dispatch is on
                // the record rather than in the journal: it was accepted, not consumed.
                record.status shouldBe WorkflowStatus.Awaiting
                record.awaiting shouldBe "approval"
                record.signals.keys shouldBe setOf("dispatch")
                calls.all() shouldBe listOf("reserve")
            }

            scenario("it survives an instance that was not even close to the wait") {
                val calls = Calls()
                val flow = shipping(calls, coolOff = 30.milliseconds)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                // Parked on the cool-off, three nodes short of the wait — the moment a callback is
                // most likely to land, and the one the old single-slot delivery refused outright.
                engine.signal(id, APPROVAL, Approval(by = "ops")).status shouldBe WorkflowStatus.Sleeping
                calls.all() shouldBe listOf("reserve")

                delay(60)
                val record = engine.resume(id)

                record.status shouldBe WorkflowStatus.Awaiting
                record.awaiting shouldBe "dispatch"
                calls.all() shouldBe listOf("reserve", "approved", "charge")
            }

            scenario("the last one under a name is the one the wait reads") {
                val calls = Calls()
                val flow = shipping(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                engine.signal(id, DISPATCH, Approval(by = "warehouse"))
                engine.signal(id, DISPATCH, Approval(by = "courier"))
                val record = engine.signal(id, APPROVAL, Approval(by = "ops"))

                record.status shouldBe WorkflowStatus.Completed
                record.succeeded("dispatch")?.value.toString() shouldBe """{"by":"courier"}"""
            }

            scenario("a lock somebody else holds makes it wait, not drop the payload") {
                val calls = Calls()
                val flow = shipping(calls)
                val store = InMemoryStore()
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                coroutineScope {
                    val held = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    val holder =
                        launch {
                            store.guarded(id) {
                                held.complete(Unit)
                                release.await()
                            }
                        }
                    held.await()

                    // The instance is mid-advance somewhere else, which is what a provider's
                    // callback races against. Handing back what the store has would lose the
                    // approval; the delivery waits for the lock instead.
                    val delivered = async { engine.signal(id, APPROVAL, Approval(by = "ops")) }
                    delay(150)
                    release.complete(Unit)

                    // Not dropped: the approval was read, and the run carried on to the next wait.
                    delivered.await().awaiting shouldBe "dispatch"
                    holder.join()
                }

                calls.all() shouldBe listOf("reserve", "approved", "charge")
            }

            scenario("a lock that never frees is a conflict the caller can retry, not a payload gone quiet") {
                val flow = shipping(Calls())
                val store = InMemoryStore()
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                coroutineScope {
                    val held = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    val holder =
                        launch {
                            store.guarded(id) {
                                held.complete(Unit)
                                release.await()
                            }
                        }
                    held.await()

                    shouldThrow<WorkflowConflictException> { engine.signal(id, APPROVAL, Approval(by = "ops")) }

                    release.complete(Unit)
                    holder.join()
                }

                // Nothing was written, which is what makes the caller's retry safe to make.
                engine.record(id)?.signals shouldBe emptyMap()
            }

            scenario("a name the workflow has no await for is refused rather than filed away") {
                val rejection = signal<Approval>("rejection")
                val flow = shipping(Calls())
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                shouldThrow<WorkflowUnknownSignalException> { engine.signal(id, rejection, Approval(by = "ops")) }

                engine.record(id)?.signals shouldBe emptyMap()
            }
        }
    })
