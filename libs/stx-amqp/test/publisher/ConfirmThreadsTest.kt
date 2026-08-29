package com.strange.amqp.publisher

import com.strange.amqp.AmqpTestBroker
import com.strange.amqp.topology.declare
import com.strange.amqp.topology.delete
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Which threads the broker's answers arrive on, established against the real client rather than
 * assumed — because [AmqpPublisher] used to depend on the answer without knowing it.
 *
 * The old design marked a returned message with a `@Volatile` field written by the return listener
 * and read by the confirm listener. That is either redundant or load-bearing depending on whether
 * those two callbacks share a thread, and nothing in the client's documentation says. The mailbox
 * removed the question — one coroutine owns the field now — but the fact is worth writing down: it
 * is what a future reader will want when they wonder whether the annotation could come back.
 */
class ConfirmThreadsTest :
    FeatureSpec({

        feature("a confirm and a return for the same message").config(enabled = AmqpTestBroker.available) {
            scenario("both callbacks arrive on the client's own thread, and neither is a coroutine's") {
                AmqpTestBroker.amqp { amqp ->
                    val exchange = AmqpTestBroker.name("nowhere")
                    val topology = amqp.declare { exchange(exchange) }
                    val channel = amqp.openChannel()

                    val returnedOn = CompletableDeferred<String>()
                    val confirmedOn = CompletableDeferred<String>()

                    try {
                        channel.confirmSelect()
                        channel.addReturnListener { returnedOn.complete(Thread.currentThread().name) }
                        channel.addConfirmListener(
                            { _, _ -> confirmedOn.complete(Thread.currentThread().name) },
                            { _, _ -> confirmedOn.complete(Thread.currentThread().name) },
                        )

                        // Mandatory and bound to nothing: the broker returns it, then confirms it.
                        channel.basicPublish(exchange, "nobody.listens", true, null, "{}".toByteArray())

                        val (onReturn, onConfirm) = withTimeout(10.seconds) { returnedOn.await() to confirmedOn.await() }

                        /* The answer, as of this client version: one connection thread delivers
                           both, in the order the broker sent them. A @Volatile between them was
                           therefore never buying anything — and a blocking call inside either
                           would stall the other. */
                        onReturn shouldBe onConfirm
                        onReturn shouldNotBe Thread.currentThread().name
                    } finally {
                        runCatching { channel.close() }
                        amqp.delete(topology)
                    }
                }
            }
        }
    })
