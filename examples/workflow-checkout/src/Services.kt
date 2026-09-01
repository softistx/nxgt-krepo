package com.strange.example.workflow

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The three remote services a checkout talks to, faked just enough to be interesting.
 *
 * [Payments] is the one worth reading. It **deduplicates on the idempotency key**, the way a real
 * gateway does, which is what makes the crash scenario show something rather than assert something:
 * the charge is attempted twice and the money moves once, and the reason is a string the engine
 * handed the step.
 */
class Warehouse {
    private val reservations = mutableMapOf<String, List<String>>()
    private var next = 0

    suspend fun reserve(items: List<String>): String {
        delay(20)
        return synchronized(this) {
            val id = "res-${++next}"
            reservations[id] = items
            say("reserved ${items.size} item(s) -> $id")
            id
        }
    }

    suspend fun release(id: String) {
        delay(20)
        synchronized(this) {
            if (reservations.remove(id) != null) say("released $id") else say("$id was not held; nothing to release")
        }
    }

    fun held(): Int = synchronized(this) { reservations.size }

    private fun say(what: String) = println("        warehouse: $what")
}

class Payments(
    /**
     * Staging for the crash scenario, and nothing a real gateway would have.
     *
     * When set, the first charge goes through and *then* the call hangs — which leaves the process
     * inside the window the whole design turns on: the effect has happened and the checkpoint has
     * not. Killing it there is what a crash is.
     */
    private val hangAfterFirstCharge: CompletableDeferred<Unit>? = null,
) {
    private val byKey = mutableMapOf<String, String>()
    private val firstCharge = AtomicBoolean(true)
    private var charges = 0
    private var refunds = 0

    /**
     * Charges the card — unless this exact [key] has been charged before, in which case it hands
     * back what it did the first time and no money moves.
     *
     * That is the contract `StepScope.idempotencyKey` is written against, and the reason a
     * checkpointed engine can be at-least-once without being dangerous.
     */
    suspend fun charge(
        card: String,
        amount: Int,
        key: String,
    ): String {
        delay(30)
        val id =
            synchronized(this) {
                val seen = byKey[key]
                if (seen != null) {
                    say("'$key' was charged already -> $seen; no money moved")
                    seen
                } else {
                    val fresh = "chg-${++charges}"
                    byKey[key] = fresh
                    say("charged $amount from $card -> $fresh   [key $key]")
                    fresh
                }
            }

        if (hangAfterFirstCharge != null && firstCharge.compareAndSet(true, false)) {
            hangAfterFirstCharge.complete(Unit)
            awaitCancellation()
        }
        return id
    }

    suspend fun refund(chargeId: String) {
        delay(20)
        synchronized(this) {
            refunds++
            say("refunded $chargeId")
        }
    }

    /** How many times money actually moved — not how many times the step ran. */
    fun charged(): Int = synchronized(this) { charges }

    fun refunded(): Int = synchronized(this) { refunds }

    private fun say(what: String) = println("        payments:  $what")
}

class Courier(
    /** When true, every booking fails — which is how the compensation scenario gets started. */
    private val onStrike: Boolean = false,
) {
    private var next = 0

    suspend fun book(order: String): String {
        delay(40)
        if (onStrike) {
            println("        courier:   no van for $order")
            error("no courier available")
        }
        return synchronized(this) {
            val id = "trk-${++next}"
            println("        courier:   booked $order -> $id")
            id
        }
    }

    suspend fun cancel(trackingId: String) {
        delay(20)
        println("        courier:   cancelled $trackingId")
    }
}

class Notifier {
    suspend fun confirm(
        order: String,
        express: Boolean,
    ) {
        delay(10)
        println("        notifier:  told the customer $order is on its way${if (express) " overnight" else ""}")
    }
}
