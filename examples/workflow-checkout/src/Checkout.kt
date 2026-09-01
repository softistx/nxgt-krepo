package com.strange.example.workflow

import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import com.strange.redis.deleteKeys
import com.strange.workflow.WorkflowEngine
import com.strange.workflow.redis.RedisWorkflowStore
import com.strange.workflow.store.WorkflowRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

private const val URI = "redis://localhost:6379/15"

/**
 * Three checkouts against a real Redis: one that works, one that has to be undone, and one whose
 * process dies halfway through.
 *
 * ```
 * ./kotlin run -m workflow-checkout
 * ```
 *
 * It needs a Redis on `localhost:6379` — the workspace's own will do — and it writes to database 15
 * under its own namespace, which it deletes on the way out.
 */
fun main() =
    runBlocking {
        val redis =
            runCatching { Redis.connect(RedisConfig(URI, namespace = "checkout-demo")).also { it.ping() } }
                .getOrElse {
                    println("This example needs a Redis on localhost:6379 — none answered ($URI).")
                    return@runBlocking
                }

        redis.use {
            try {
                happyPath(redis)
                somethingGoesWrong(redis)
                theProcessDies(redis)
            } finally {
                // Relative to this connection's namespace, so it takes out this demo's keys and
                // nothing else on database 15.
                redis.deleteKeys()
            }
        }
    }

/** Everything works. Three nodes, two of them concurrent, and one fork. */
private suspend fun happyPath(redis: Redis) {
    heading("1. A checkout that works")

    val payments = Payments()
    val warehouse = Warehouse()
    val flow = checkoutWorkflow(warehouse, payments, Courier(), Notifier())
    val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(flow) }

    val instance = engine.start(flow, order("ord-1", express = true), id = "ord-1")

    println()
    println("      status      ${instance.status}")
    println("      context     ${instance.context}")
    journal(instance.record)
    println("      money moved ${payments.charged()} time(s), ${warehouse.held()} reservation(s) still held")
}

/**
 * The courier has no van. The charge that had already gone through is refunded and the stock is
 * released — newest first, which is the only order that makes sense.
 */
private suspend fun somethingGoesWrong(redis: Redis) {
    heading("2. A checkout that has to be undone")

    val payments = Payments()
    val warehouse = Warehouse()
    val flow = checkoutWorkflow(warehouse, payments, Courier(onStrike = true), Notifier())
    val engine = WorkflowEngine(RedisWorkflowStore(redis)) { register(flow) }

    val instance = engine.start(flow, order("ord-2"), id = "ord-2")

    println()
    println("      status      ${instance.status}")
    println("      failed at   ${instance.error?.node}")
    journal(instance.record)
    println("      money moved ${payments.charged()} time(s) and came back ${payments.refunded()} time(s)")
    println("      ${warehouse.held()} reservation(s) still held")
}

/**
 * The interesting one.
 *
 * The gateway is told to take the money and then hang, so the process is killed in the one window
 * this whole design is about: **the effect has happened and the checkpoint has not**. A second
 * engine — a restarted process, as far as Redis is concerned — picks the instance up, runs the
 * charge again because nothing recorded that it had run, and the gateway recognises the key and
 * moves no money. That is what at-least-once buys, and what it costs.
 */
private suspend fun theProcessDies(redis: Redis) {
    heading("3. A checkout whose process dies mid-charge")

    val reachedTheGateway = CompletableDeferred<Unit>()
    val payments = Payments(hangAfterFirstCharge = reachedTheGateway)
    val warehouse = Warehouse()
    val flow = checkoutWorkflow(warehouse, payments, Courier(), Notifier())
    val store = RedisWorkflowStore(redis, lease = 200.milliseconds)

    val dying = WorkflowEngine(store) { register(flow) }
    val scope = CoroutineScope(Job() + Dispatchers.Default)
    val run = scope.launch { dying.start(flow, order("ord-3"), id = "ord-3") }
    reachedTheGateway.await()
    run.cancelAndJoin()

    println()
    println("      -- the process is gone --")
    val abandoned = store.load("ord-3")!!
    println("      status      ${abandoned.status}")
    journal(abandoned)
    println("      the charge is not in the journal, and the money has already left.")
    println()

    // The lock the dead process held is not released by a coroutine that is being cancelled; it is
    // left to expire, which is what the lease is for.
    kotlinx.coroutines.delay(300.milliseconds)

    println("      -- a new process picks it up --")
    val recovered = WorkflowEngine(store) { register(flow) }
    val instance = recovered.resume(flow, "ord-3")

    println()
    println("      status      ${instance.status}")
    println("      context     ${instance.context}")
    journal(instance.record)
    println("      the charge step ran twice; money moved ${payments.charged()} time(s).")
}

private fun order(
    id: String,
    express: Boolean = false,
) = Order(id = id, items = listOf("kettle", "mug"), total = 4_200, card = "4242", express = express)

private fun journal(record: WorkflowRecord) {
    println("      journal")
    record.journal.forEach { entry ->
        val attempts = if (entry.attempts > 1) "  (${entry.attempts} attempts)" else ""
        println("        ${entry.outcome.name.padEnd(18)} ${entry.node}$attempts")
    }
}

private fun heading(title: String) {
    println()
    println("=".repeat(72))
    println("  $title")
    println("=".repeat(72))
}
