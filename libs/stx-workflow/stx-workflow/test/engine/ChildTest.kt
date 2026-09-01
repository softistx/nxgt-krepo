package com.softistx.workflow.engine

import com.softistx.workflow.ChildFailedException
import com.softistx.workflow.ChildTimeoutException
import com.softistx.workflow.Workflow
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowNotUndoableException
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.await
import com.softistx.workflow.dsl.child
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.signal
import com.softistx.workflow.dsl.step
import com.softistx.workflow.dsl.within
import com.softistx.workflow.fixture.Approval
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.workflow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val COURIER = signal<Approval>("courier")

/**
 * A workflow that delegates to another workflow.
 *
 * The child is a real instance — its own record, its own journal, its own compensations — and that
 * is what these specs are about. A fulfilment that parks for two days on a courier's callback cannot
 * be a function call inside the parent's step, and undoing the parent has to mean undoing the child
 * rather than whatever the parent happened to remember about it.
 */
class ChildTest :
    FeatureSpec({
        fun fulfilment(
            calls: Calls,
            parks: Boolean = false,
        ) = workflow<Ledger>("fulfilment") {
            step("book") {
                calls.record("book")
                context.copy(booking = "b-1")
            } compensate {
                calls.record("unbook")
            }

            if (parks) {
                await(COURIER) { courier ->
                    calls.record("collected")
                    context.copy(note = courier.by)
                }
            }
        }

        fun ordering(
            calls: Calls,
            fulfil: Workflow<Ledger>,
            fails: Boolean = false,
        ) = workflow<Ledger>("ordering") {
            step("reserve") {
                calls.record("reserve")
                context.copy(reservationId = "r-1")
            } compensate {
                calls.record("release")
            }

            child("fulfil", fulfil, with = { Ledger(express = context.express) }) { done ->
                calls.record("fulfilled")
                context.copy(booking = done.booking)
            }

            step("confirm") {
                calls.record("confirm")
                if (fails) error("the till is down")
                context
            }
        }

        feature("running a child") {
            scenario("its final context folds into the parent's, and the parent goes on") {
                val calls = Calls()
                val child = fulfilment(calls)
                val parent = ordering(calls, child)
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(child)
                        register(parent)
                    }

                val instance = engine.start(parent, Ledger())

                instance.status shouldBe WorkflowStatus.Completed
                instance.context.booking shouldBe "b-1"
                calls.all() shouldBe listOf("reserve", "book", "fulfilled", "confirm")
            }

            scenario("the child is an instance of its own, named after the parent and the node") {
                val calls = Calls()
                val child = fulfilment(calls)
                val parent = ordering(calls, child)
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(child)
                        register(parent)
                    }

                val id = engine.start(parent, Ledger(), id = "order-1").id

                id shouldBe "order-1"
                val childRecord = engine.record("order-1/fulfil").shouldNotBeNull()
                childRecord.workflow shouldBe "fulfilment"
                childRecord.status shouldBe WorkflowStatus.Completed
                childRecord.parent shouldBe "order-1"
            }
        }

        feature("a child that stops to wait") {
            scenario("the parent parks on it, and finishing the child finishes the parent") {
                val calls = Calls()
                val child = fulfilment(calls, parks = true)
                val parent = ordering(calls, child)
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(child)
                        register(parent)
                    }

                val parked = engine.start(parent, Ledger(), id = "order-2")

                parked.status shouldBe WorkflowStatus.Awaiting
                parked.record.awaiting shouldBe "order-2/fulfil"
                calls.all() shouldBe listOf("reserve", "book")

                // The child wakes its parent as it finishes; nothing polls in this test.
                engine.signal("order-2/fulfil", COURIER, Approval(by = "dhl"))

                engine.record("order-2").shouldNotBeNull().status shouldBe WorkflowStatus.Completed
                calls.all() shouldBe listOf("reserve", "book", "collected", "fulfilled", "confirm")
            }

            scenario("a parent waiting on a child is due, so a worker comes back for it") {
                val store = InMemoryStore()
                val calls = Calls()
                val child = fulfilment(calls, parks = true)
                val parent = ordering(calls, child)
                val engine =
                    WorkflowEngine(store) {
                        register(child)
                        register(parent)
                    }

                engine.start(parent, Ledger(), id = "order-3")

                // Unlike an `await` with no deadline, this one is polled: the child's own wake-up is
                // prompt but not durable, and the poll is what covers a process dying in between.
                store.runnable(Clock.System.now() + 2.minutes, limit = 10) shouldBe listOf("order-3")
            }
        }

        feature("when the wake-up does not arrive") {
            scenario("the poll gets the parent moving again") {
                val calls = Calls()
                val store = InMemoryStore()
                val fulfil = fulfilment(calls, parks = true)
                val parent = ordering(calls, fulfil)
                val together =
                    WorkflowEngine(store) {
                        register(fulfil)
                        register(parent)
                    }
                together.start(parent, Ledger(), id = "order-6")

                // An engine that has never heard of the parent stands in for the process that died
                // between the child's terminal write and telling anybody about it: the child
                // finishes, and nothing wakes the parent.
                val alone = WorkflowEngine(store) { register(fulfil) }
                alone.signal("order-6/fulfil", COURIER, Approval(by = "dhl"))

                together.record("order-6").shouldNotBeNull().status shouldBe WorkflowStatus.Awaiting

                // Which is what the poll is for.
                together.resume("order-6").status shouldBe WorkflowStatus.Completed
            }

            scenario("a child that outstays its within fails the node") {
                val calls = Calls()
                val fulfil = fulfilment(calls, parks = true)
                val slow =
                    workflow<Ledger>("ordering") {
                        step("reserve") {
                            calls.record("reserve")
                            context.copy(reservationId = "r-1")
                        } compensate {
                            calls.record("release")
                        }

                        child("fulfil", fulfil, with = { Ledger() }) { context } within 30.milliseconds
                    }
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(fulfil)
                        register(slow)
                    }
                engine.start(slow, Ledger(), id = "order-7")

                delay(60)
                val record = engine.resume("order-7")

                record.status shouldBe WorkflowStatus.Compensated
                record.error?.type shouldBe ChildTimeoutException::class.qualifiedName
                // The child is left where it was; the parent's unwind undoes it on the way back.
                calls.all() shouldBe listOf("reserve", "book", "unbook", "release")
            }
        }

        feature("undoing a parent") {
            scenario("it undoes the child, which runs the child's own compensations") {
                val calls = Calls()
                val child = fulfilment(calls)
                val parent = ordering(calls, child, fails = true)
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(child)
                        register(parent)
                    }

                val instance = engine.start(parent, Ledger(), id = "order-4")

                instance.status shouldBe WorkflowStatus.Compensated
                calls.all() shouldBe listOf("reserve", "book", "fulfilled", "confirm", "unbook", "release")
                engine.record("order-4/fulfil").shouldNotBeNull().status shouldBe WorkflowStatus.Cancelled
            }

            scenario("a child that could not do its job fails the node and unwinds the parent") {
                val calls = Calls()
                val child =
                    workflow<Ledger>("fulfilment") {
                        step("book") {
                            calls.record("book")
                            error("no courier available")
                        }
                    }
                val parent = ordering(calls, child)
                val engine =
                    WorkflowEngine(InMemoryStore()) {
                        register(child)
                        register(parent)
                    }

                val instance = engine.start(parent, Ledger(), id = "order-5")

                instance.status shouldBe WorkflowStatus.Compensated
                instance.record.error?.type shouldBe ChildFailedException::class.qualifiedName
                calls.all() shouldBe listOf("reserve", "book", "release")
            }
        }

        feature("undo on its own") {
            scenario("it reverses a completed instance") {
                val calls = Calls()
                val child = fulfilment(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(child) }
                val id = engine.start(child, Ledger(), id = "f-1").id

                val undone = engine.undo(id)

                undone.status shouldBe WorkflowStatus.Cancelled
                calls.all() shouldBe listOf("book", "unbook")
            }

            scenario("it refuses an instance that is not finished, which is cancel's job") {
                val calls = Calls()
                val child = fulfilment(calls, parks = true)
                val engine = WorkflowEngine(InMemoryStore()) { register(child) }
                val id = engine.start(child, Ledger(), id = "f-2").id

                val refused = shouldThrow<WorkflowNotUndoableException> { engine.undo(id) }

                refused.status shouldBe WorkflowStatus.Awaiting
            }

            scenario("it refuses an instance already unwound, rather than taking back a compensation") {
                val calls = Calls()
                val child = fulfilment(calls)
                val engine = WorkflowEngine(InMemoryStore()) { register(child) }
                val id = engine.start(child, Ledger(), id = "f-3").id
                engine.undo(id)

                shouldThrow<WorkflowNotUndoableException> { engine.undo(id) }

                calls.all() shouldBe listOf("book", "unbook")
            }
        }
    })
