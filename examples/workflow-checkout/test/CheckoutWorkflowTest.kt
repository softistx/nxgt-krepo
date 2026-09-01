package com.strange.example.workflow

import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.store.NodeOutcome
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * The same three runs `main` narrates, asserted instead of printed — against `InMemoryStore`, so the
 * example is verified on every build rather than only when somebody has a Redis up.
 */
private fun order(
    id: String,
    express: Boolean = false,
) = Order(id = id, items = listOf("kettle", "mug"), total = 4_200, card = "4242", express = express)

class CheckoutWorkflowTest :
    FeatureSpec({
        feature("a checkout that works") {
            scenario("it charges once, books once, and takes the arm the order asked for") {
                val payments = Payments()
                val warehouse = Warehouse()
                val flow = checkoutWorkflow(warehouse, payments, Courier(), Notifier())
                val instance =
                    WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, order("ord-1", express = true), "ord-1")

                instance.status shouldBe WorkflowStatus.Completed
                instance.context.chargeId shouldBe "chg-1"
                instance.context.trackingId shouldBe "trk-1"
                payments.charged() shouldBe 1
                warehouse.held() shouldBe 1
                (instance.record.succeeded("notify/express/notify-express") != null) shouldBe true
            }
        }

        feature("a checkout that cannot be finished") {
            scenario("the charge is refunded and the stock released, newest first") {
                val payments = Payments()
                val warehouse = Warehouse()
                val flow = checkoutWorkflow(warehouse, payments, Courier(onStrike = true), Notifier())
                val instance =
                    WorkflowEngine(InMemoryStore()) { register(flow) }.start(flow, order("ord-2"), "ord-2")

                instance.status shouldBe WorkflowStatus.Compensated
                instance.error!!.node shouldBe "provision/tracking"
                payments.refunded() shouldBe 1
                warehouse.held() shouldBe 0

                // The fan-out is retried, and the retry re-runs only the leg that had not succeeded:
                // the card is not charged a second time on the way to failing.
                payments.charged() shouldBe 1
                instance.record.latest("provision/tracking")!!.attempts shouldBe 2
                instance.record.latest("provision/charge")!!.outcome shouldBe NodeOutcome.Compensated
            }
        }

        feature("a checkout whose process dies between the effect and the checkpoint") {
            scenario("the charge runs a second time and the money moves once") {
                val store = InMemoryStore()
                val reachedTheGateway = CompletableDeferred<Unit>()
                val payments = Payments(hangAfterFirstCharge = reachedTheGateway)
                val flow = checkoutWorkflow(Warehouse(), payments, Courier(), Notifier())

                val scope = CoroutineScope(Job() + Dispatchers.Default)
                val dying = scope.launch { WorkflowEngine(store) { register(flow) }.start(flow, order("ord-3"), "ord-3") }
                reachedTheGateway.await()
                dying.cancelAndJoin()

                // The window the whole design is about: the money has left, and nothing recorded it.
                val abandoned = store.load("ord-3")!!
                abandoned.status shouldBe WorkflowStatus.Running
                abandoned.succeeded("provision/charge") shouldBe null
                payments.charged() shouldBe 1

                val instance = WorkflowEngine(store) { register(flow) }.resume(flow, "ord-3")

                instance.status shouldBe WorkflowStatus.Completed
                instance.context.chargeId shouldBe "chg-1"
                // The step ran twice; the gateway recognised the key the engine handed it.
                payments.charged() shouldBe 1
            }
        }
    })
