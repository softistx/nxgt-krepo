package com.strange.common.coroutines

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds

/**
 * The bridge out of a callback, tested the way it is used: written to from a thread that knows
 * nothing about coroutines, read by exactly one that does.
 *
 * The claims worth pinning down are the two the design rests on — that posting from a foreign
 * thread never blocks it, and that messages come out in the order they went in, which is what lets
 * the consumer own its state without any synchronisation.
 */
class MailboxTest :
    FeatureSpec({

        feature("posting from another thread") {
            scenario("a plain Java thread can hand messages over, and they all arrive in order") {
                val mailbox = Mailbox<Int>()
                val received = mutableListOf<Int>()
                val done = CompletableDeferred<Unit>()

                coroutineScope {
                    val consumer =
                        launch {
                            mailbox.consume { message ->
                                received += message
                                if (received.size == 100) done.complete(Unit)
                            }
                        }

                    // No coroutine anywhere near this: it is what a client's I/O thread looks like.
                    val posted = CountDownLatch(1)
                    Thread {
                        (1..100).forEach { mailbox.post(it) }
                        posted.countDown()
                    }.start()
                    posted.await()

                    withTimeout(5.seconds) { done.await() }
                    mailbox.close()
                    consumer.join()
                }

                received shouldContainExactly (1..100).toList()
            }
        }

        feature("draining") {
            scenario("closing lets the consumer finish the backlog first") {
                /* This is what makes a shutdown able to answer the callers still waiting: the
                   messages already posted are applied, and only then does consume return. */
                val mailbox = Mailbox<String>()
                (1..5).forEach { mailbox.post("m$it") }
                mailbox.close()

                val received = mutableListOf<String>()
                withTimeout(5.seconds) { mailbox.consume { received += it } }

                received shouldContainExactly listOf("m1", "m2", "m3", "m4", "m5")
            }

            scenario("use closes it, for the block that owns the whole exchange") {
                /* AutoCloseable because it has a lifetime and something has to end it. Its usual
                   owner is an object, not a block — but where a block does own it, `use` is what a
                   reader of this codebase will reach for, so it works. */
                val mailbox =
                    Mailbox<String>().use { open ->
                        open.post("inside") shouldBe true
                        open
                    }

                mailbox.post("after") shouldBe false
            }

            scenario("a message posted after closing is refused rather than lost quietly") {
                val mailbox = Mailbox<String>()
                mailbox.close()

                mailbox.post("late") shouldBe false
            }
        }

        feature("a consumer with a turn of its own") {
            scenario("tryReceive takes what is waiting and gives up when there is nothing") {
                // The shape a poll loop wants: apply what has arrived, then get back to polling.
                val mailbox = Mailbox<String>()
                mailbox.post("a")
                mailbox.post("b")

                val drained = generateSequence { mailbox.tryReceive() }.toList()

                drained shouldContainExactly listOf("a", "b")
                mailbox.tryReceive() shouldBe null
            }
        }
    })
