package com.softistx.amqp.consumer

import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpTestBroker
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Where the client delivers, and what a delivery callback that never returns costs the others —
 * asked of the real client with its own callbacks, no [AmqpConsumer] involved. The consumer-level
 * counterparts are in `ConsumerDeliveryTest`.
 *
 * [AmqpConsumer] hands a delivery to its flow with `trySendBlocking`, which parks the delivering
 * thread whenever the flow's buffer is full. Whether that is harmless depends on which thread it
 * parks and on who else needs it, and nothing in the client's documentation says. A callback
 * blocked on a latch is the same thing as `trySendBlocking` on a full buffer: a thread that does
 * not come back until the collector makes room.
 *
 * What the client answered (amqp-client 5.x, `ConsumerWorkService`):
 * - Deliveries arrive on a thread of the connection's *shared executor* (`pool-N-thread-M` by
 *   default, `2 x cores` threads), not on the socket reader (`AMQP Connection host:port`).
 * - Work is serialised per channel, so one blocked consumer holds one pool thread and delays only
 *   its own channel: a sibling consumer on the same connection is still served.
 * - The pool is shared, though: once as many callbacks are blocked as the pool has threads, a
 *   sibling's delivery waits until one of them returns. That is the starvation
 *   [ConsumerOptions.MAX_PREFETCH] exists to rule out — a consumer cannot have more unacknowledged
 *   messages than its prefetch, so a prefetch within the buffer never fills it.
 */
class DeliveryThreadsTest :
    FeatureSpec({

        /** A consumer whose callback parks on [release] after announcing itself on [entered]. */
        fun blocked(
            amqp: Amqp,
            release: CountDownLatch,
            entered: CountDownLatch,
        ) = DeliveryQueue(amqp, messages = 3, prefetch = 3) { _, _ ->
            entered.countDown()
            release.await()
        }

        feature("a delivery callback that does not return").config(enabled = AmqpTestBroker.available) {
            scenario("deliveries arrive on the shared pool, and one blocked consumer does not delay a sibling") {
                val release = CountDownLatch(1)
                val entered = CountDownLatch(1)
                connected(executor = null) { amqp ->
                    val stuck = blocked(amqp, release, entered)
                    val delivered = CompletableDeferred<String>()
                    val sibling = sibling(amqp, delivered)
                    try {
                        entered.await(10, TimeUnit.SECONDS) shouldBe true
                        val thread = withTimeout(5.seconds) { delivered.await() }
                        println("Threads: sibling delivered on [$thread], another callback parked")

                        thread shouldStartWith "pool-"
                    } finally {
                        release.countDown()
                        stuck.close()
                        sibling.close()
                    }
                }
            }

            scenario("as many blocked callbacks as the pool has threads starve a sibling until one returns") {
                val poolSize = 2
                val executor =
                    Executors.newFixedThreadPool(poolSize) { Thread(it, "spec-delivery").apply { isDaemon = true } }
                val release = CountDownLatch(1)
                val entered = CountDownLatch(poolSize - 1)
                try {
                    connected(executor) { amqp ->
                        val stuck = mutableListOf<DeliveryQueue>()
                        val siblings = mutableListOf<DeliveryQueue>()
                        try {
                            // One short of the pool: the sibling still gets the free thread.
                            stuck += blocked(amqp, release, entered)
                            entered.await(10, TimeUnit.SECONDS) shouldBe true
                            val first = CompletableDeferred<String>()
                            siblings += sibling(amqp, first)
                            val served = withTimeoutOrNull(5.seconds) { first.await() }
                            println("Threads: ${poolSize - 1}/$poolSize threads parked: sibling on [$served]")
                            served shouldBe "spec-delivery"

                            // The pool exhausted: the next sibling waits for as long as the callbacks do.
                            val enteredAll = CountDownLatch(1)
                            stuck += blocked(amqp, release, enteredAll)
                            enteredAll.await(10, TimeUnit.SECONDS) shouldBe true
                            val second = CompletableDeferred<String>()
                            siblings += sibling(amqp, second)
                            val starved = withTimeoutOrNull(1500.milliseconds) { second.await() }
                            println("Threads: $poolSize/$poolSize threads parked: sibling after 1.5 s -> $starved")
                            starved shouldBe null

                            release.countDown()
                            withTimeout(10.seconds) { second.await() } shouldBe "spec-delivery"
                        } finally {
                            release.countDown()
                            (stuck + siblings).forEach { it.close() }
                        }
                    }
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    })
