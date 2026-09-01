package com.softistx.workflow.engine

import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowStatus
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.step
import com.softistx.workflow.fixture.Calls
import com.softistx.workflow.fixture.Ledger
import com.softistx.workflow.fixture.Wobble
import com.softistx.workflow.store.InMemoryStore
import com.softistx.workflow.workflow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * What a deploy does to the instances that are already in flight.
 *
 * The engine matches the journal to the declaration **by node name**, in both directions:
 * `Runner.runStep` skips a node whose last entry says it succeeded, and `Unwind.nextUndo` will only
 * compensate an entry whose node the current declaration still has a compensation for. Nothing else
 * is consulted — there is no definition version, no hash, and no refusal to resume an instance that
 * a different version of the code started.
 *
 * That is a deliberate shape: a workflow is code, and code gets redeployed while instances are
 * running. It is also a shape with two sharp edges, and these are them. They are asserted rather
 * than described, because a README paragraph about what *would* happen is a paragraph nobody can
 * check.
 *
 * Each scenario uses `ResumeTest`'s manoeuvre — cancel the scope mid-step, which leaves exactly what
 * killing the process leaves — and then hands the stored instance to an engine holding a
 * **different** declaration under the same name. That is what the pod running the new build has.
 */
class DefinitionChangeTest :
    FeatureSpec({
        feature("a step renamed between two deploys") {
            scenario("it runs a second time, and what it did under the old name is never undone") {
                val calls = Calls()
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()

                val before =
                    workflow<Ledger>("rename") {
                        step("reserve") {
                            calls.record("reserve")
                            context.copy(reservationId = "r-1")
                        } compensate { calls.record("undo:reserve") }
                        step("charge") {
                            reached.complete(Unit)
                            awaitCancellation()
                        }
                    }

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running = scope.launch { WorkflowEngine(store) { register(before) }.start(before, Ledger(), "run-1") }
                reached.await()
                running.cancelAndJoin()

                store.load("run-1")!!.journal.map { it.node } shouldBe listOf("reserve")

                // The new build renames `reserve` to `hold`. Same workflow name, same instance.
                val after =
                    workflow<Ledger>("rename") {
                        step("hold") {
                            calls.record("hold")
                            context.copy(reservationId = "r-2")
                        } compensate { calls.record("undo:hold") }
                        step("charge") { throw Wobble("declined") }
                    }
                val instance = WorkflowEngine(store) { register(after) }.resume(after, "run-1")

                instance.status shouldBe WorkflowStatus.Compensated
                // The reservation was taken twice — the journal's `reserve` matches no node the new
                // declaration has, so nothing skipped `hold`.
                calls.count("reserve") shouldBe 1
                calls.count("hold") shouldBe 1
                // And only the second one was released. `Unwind.nextUndo` needs `workflow.undo` to
                // hold the entry's node; after the rename it does not hold `reserve`, so r-1 stays
                // reserved with nothing left that knows about it.
                calls.count("undo:hold") shouldBe 1
                calls.count("undo:reserve") shouldBe 0
            }
        }

        feature("a step deleted between two deploys") {
            scenario("its entry stays in the journal, and the unwind steps straight over it") {
                val calls = Calls()
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()

                val before =
                    workflow<Ledger>("delete") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate
                            { calls.record("undo:reserve") }
                        step("charge") { context.copy(chargeId = "c-1") } compensate
                            { calls.record("undo:charge") }
                        step("ship") {
                            reached.complete(Unit)
                            awaitCancellation()
                        }
                    }

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running = scope.launch { WorkflowEngine(store) { register(before) }.start(before, Ledger(), "run-1") }
                reached.await()
                running.cancelAndJoin()

                store.load("run-1")!!.journal.map { it.node } shouldBe listOf("reserve", "charge")

                // The new build drops `charge` — the money has already moved.
                val after =
                    workflow<Ledger>("delete") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate
                            { calls.record("undo:reserve") }
                        step("ship") { throw Wobble("no courier") }
                    }
                val instance = WorkflowEngine(store) { register(after) }.resume(after, "run-1")

                instance.status shouldBe WorkflowStatus.Compensated
                // "Compensated" here means *every compensation this declaration owed* ran. It does
                // not mean the instance was fully undone: c-1 was charged and there is no longer any
                // code that refunds it.
                calls.count("undo:reserve") shouldBe 1
                calls.count("undo:charge") shouldBe 0
            }
        }

        feature("a step reordered between two deploys") {
            scenario("the unwind still follows what happened, not what the declaration now lists") {
                val calls = Calls()
                val store = InMemoryStore()
                val reached = CompletableDeferred<Unit>()

                val before =
                    workflow<Ledger>("reorder") {
                        step("reserve") { context.copy(reservationId = "r-1") } compensate
                            { calls.record("undo:reserve") }
                        step("charge") { context.copy(chargeId = "c-1") } compensate
                            { calls.record("undo:charge") }
                        step("ship") {
                            reached.complete(Unit)
                            awaitCancellation()
                        }
                    }

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val running = scope.launch { WorkflowEngine(store) { register(before) }.start(before, Ledger(), "run-1") }
                reached.await()
                running.cancelAndJoin()

                // The new build swaps the first two steps. Both already ran, in the old order.
                val after =
                    workflow<Ledger>("reorder") {
                        step("charge") { context.copy(chargeId = "c-1") } compensate
                            { calls.record("undo:charge") }
                        step("reserve") { context.copy(reservationId = "r-1") } compensate
                            { calls.record("undo:reserve") }
                        step("ship") { throw Wobble("no courier") }
                    }
                WorkflowEngine(store) { register(after) }.resume(after, "run-1")

                // `nextUndo` walks the journal in reverse, so the order comes from what happened.
                // This is the reordering that is safe, and the reason the other two are not: names
                // are what the journal and the declaration agree on, and reordering changes neither.
                calls.all() shouldBe listOf("undo:charge", "undo:reserve")
            }
        }
    })
