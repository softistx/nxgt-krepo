package com.strange.workflow.engine

import com.strange.workflow.AwaitTimeoutException
import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowNotAwaitingException
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.await
import com.strange.workflow.dsl.compensate
import com.strange.workflow.dsl.signal
import com.strange.workflow.dsl.step
import com.strange.workflow.dsl.within
import com.strange.workflow.fixture.Approval
import com.strange.workflow.fixture.Calls
import com.strange.workflow.fixture.Ledger
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

private val APPROVAL = signal<Approval>("approval")

/**
 * Waiting for a person.
 *
 * The workflow here is the shape the whole feature exists for: something is reserved, somebody has
 * to say yes, and only then is the card charged. What makes it worth a state in a store rather than
 * a suspended coroutine is that the yes may arrive in four days, from a different process, and the
 * reservation has to be released if it never arrives at all.
 */
class AwaitTest :
    FeatureSpec({
        fun approving(
            calls: Calls,
            deadline: kotlin.time.Duration? = null,
        ) = workflow<Ledger>("refund") {
            step("reserve") {
                calls.record("reserve")
                context.copy(reservationId = "r-1")
            } compensate {
                calls.record("release")
            }

            await(APPROVAL) { approval ->
                calls.record("approved")
                context.copy(approvedBy = approval.by, note = approval.note)
            }.also { node -> deadline?.let { node within it } }

            step("charge") {
                calls.record("charge")
                context.copy(chargeId = "c-1")
            }
        }

        feature("reaching an await") {
            scenario("it stops there, says what it is waiting for, and runs nothing after it") {
                val calls = Calls()
                val flow = approving(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }

                val instance = engine.start(flow, Ledger())

                instance.status shouldBe WorkflowStatus.Awaiting
                instance.record.awaiting shouldBe "approval"
                instance.record.wakeAt shouldBe null
                calls.all() shouldBe listOf("reserve")
            }

            scenario("a wait with no deadline is nothing a worker should poll for") {
                val calls = Calls()
                val flow = approving(calls)
                val store = InMemoryStore()
                val engine = WorkflowEngine(store) { register(flow) }

                engine.start(flow, Ledger())

                // The instance is alive and unfinished, and still: offering it to a worker would be
                // offering it work it cannot do. Only `signal` or `cancel` moves this one.
                store.runnable(Clock.System.now(), limit = 10).shouldBeEmpty()
            }

            scenario("resuming it changes nothing and re-runs nothing") {
                val calls = Calls()
                val flow = approving(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                calls.clear()

                val resumed = engine.resume(id)

                resumed.status shouldBe WorkflowStatus.Awaiting
                calls.all().shouldBeEmpty()
            }
        }

        feature("delivering the signal") {
            scenario("the payload folds into the context and the workflow goes on") {
                val calls = Calls()
                val flow = approving(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                val record = engine.signal(id, APPROVAL, Approval(by = "ops", note = "verified by phone"))

                record.status shouldBe WorkflowStatus.Completed
                record.awaiting shouldBe null
                record.signal shouldBe null
                calls.all() shouldBe listOf("reserve", "approved", "charge")
            }

            scenario("what arrived is kept in the journal, because nothing else in this system has it") {
                val flow = approving(Calls())
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                val record = engine.signal(id, APPROVAL, Approval(by = "ops"))

                record.succeeded("approval")?.value.shouldNotBeNull()
            }

            scenario("approving twice is refused, rather than looking like it landed") {
                val flow = approving(Calls())
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id
                engine.signal(id, APPROVAL, Approval(by = "ops"))

                val refused = shouldThrow<WorkflowNotAwaitingException> { engine.signal(id, APPROVAL, Approval(by = "ops")) }

                refused.status shouldBe WorkflowStatus.Completed
            }

            scenario("a signal nobody is waiting for is refused") {
                val other = signal<Approval>("rejection")
                val flow = approving(Calls())
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                shouldThrow<WorkflowNotAwaitingException> { engine.signal(id, other, Approval(by = "ops")) }
            }
        }

        feature("a deadline") {
            scenario("an instance with one is due at it, so a worker will come back") {
                val store = InMemoryStore()
                val flow = approving(Calls(), deadline = 1.hours)
                val engine = WorkflowEngine(store) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                store.runnable(Clock.System.now(), limit = 10).shouldBeEmpty()
                store.runnable(Clock.System.now() + 2.hours, limit = 10) shouldBe listOf(id)
            }

            scenario("passing it fails the wait and unwinds what came before") {
                val calls = Calls()
                val flow = approving(calls, deadline = 20.milliseconds)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                delay(50)
                val record = engine.resume(id)

                record.status shouldBe WorkflowStatus.Compensated
                record.error?.type shouldBe AwaitTimeoutException::class.qualifiedName
                calls.all() shouldBe listOf("reserve", "release")
            }

            scenario("a signal that beats the deadline is an ordinary approval") {
                val calls = Calls()
                val flow = approving(calls, deadline = 1.hours)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                engine.signal(id, APPROVAL, Approval(by = "ops")).status shouldBe WorkflowStatus.Completed
                calls.all() shouldBe listOf("reserve", "approved", "charge")
            }
        }

        feature("cancelling a wait") {
            scenario("it unwinds and lands Cancelled, rather than leaving the reservation behind") {
                val calls = Calls()
                val flow = approving(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(flow) }
                val id = engine.start(flow, Ledger()).id

                val record = engine.cancel(id)

                record.status shouldBe WorkflowStatus.Cancelled
                record.awaiting shouldBe null
                calls.all() shouldBe listOf("reserve", "release")
            }
        }

        feature("declaring one") {
            scenario("two waits on the same signal have to be named apart") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        workflow<Ledger>("twice") {
                            await(APPROVAL) { context }
                            await(APPROVAL) { context }
                        }
                    }

                failure.shouldBeInstanceOf<IllegalArgumentException>()
                failure.message shouldBe "workflow 'twice' declares 'approval' twice"
            }
        }
    })
