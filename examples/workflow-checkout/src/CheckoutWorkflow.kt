package com.softistx.example.workflow

import com.softistx.workflow.Workflow
import com.softistx.workflow.dsl.branch
import com.softistx.workflow.dsl.compensate
import com.softistx.workflow.dsl.exponential
import com.softistx.workflow.dsl.outcome
import com.softistx.workflow.dsl.parallel
import com.softistx.workflow.dsl.retry
import com.softistx.workflow.dsl.step
import com.softistx.workflow.dsl.timeout
import com.softistx.workflow.workflow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What the two legs of the `provision` fan-out produce.
 *
 * They are keys rather than plain names because two legs cannot both return the order — this is the
 * one place a single typed context does not stretch, and an explicit `merge` is what folds their
 * results back into it, checked by the compiler.
 */
private val CHARGE = outcome<String>("charge")
private val TRACKING = outcome<String>("tracking")

/**
 * A checkout, as a compensating workflow.
 *
 * Read it top to bottom: it is the whole of what happens, and the whole of what is undone if it
 * cannot finish. Nothing here says how any of it is stored or retried after a crash — that is the
 * engine's, and which store it uses is decided once, in [main].
 */
fun checkoutWorkflow(
    warehouse: Warehouse,
    payments: Payments,
    courier: Courier,
    notifier: Notifier,
): Workflow<Order> =
    workflow("checkout") {
        step("reserve") {
            context.copy(reservationId = warehouse.reserve(context.items))
        }.compensate {
            warehouse.release(context.reservationId!!)
        } retry {
            times = 3
            backoff = exponential(50.milliseconds, max = 1.seconds)
        }

        parallel("provision") {
            // The key is the engine's, not ours: the same string on a retry, on a resume, and in
            // another process — which is what lets the charge be attempted twice safely.
            branch(CHARGE) {
                payments.charge(context.card, context.total, key = idempotencyKey)
            }.compensate { chargeId ->
                payments.refund(chargeId)
            }

            branch(TRACKING) {
                courier.book(context.id)
            }.compensate { trackingId ->
                courier.cancel(trackingId)
            }

            merge { out ->
                context.copy(chargeId = out[CHARGE], trackingId = out[TRACKING])
            }
        } retry {
            times = 2
            backoff = exponential(100.milliseconds, max = 1.seconds)
        } timeout 10.seconds

        // A fork is said rather than written. The arm taken is written into the journal before it
        // runs, so a resume does not re-decide it against a context later steps have changed.
        branch("notify") {
            on("express", { it.express }) {
                step("notify-express") { context.also { notifier.confirm(it.id, express = true) } }
            }
            otherwise {
                step("notify-standard") { context.also { notifier.confirm(it.id, express = false) } }
            }
        }
    }
