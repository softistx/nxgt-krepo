package com.strange.amqp

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * The connection itself, against a real broker.
 *
 * Small on purpose: what is worth proving here is that the configuration reaches the client and
 * that channels come from the connection rather than from anywhere else — everything interesting
 * happens on a channel, and the specs for those are where it is tested.
 */
class AmqpTest :
    FeatureSpec({

        feature("a connection that is closed").config(enabled = AmqpTestBroker.available) {
            scenario("closing it twice is not an error") {
                // Not a hypothetical: Ktor's DI closes every AutoCloseable it hands out when the
                // application stops, and whoever built this one has its own claim to closing it.
                val amqp = AmqpTestBroker.connect("shared-amqp close")

                amqp.close()
                amqp.close()

                amqp.isOpen shouldBe false
            }
        }

        feature("connecting").config(enabled = AmqpTestBroker.available) {
            scenario("the connection is open, and hands out channels") {
                AmqpTestBroker.amqp { amqp ->
                    amqp.isOpen shouldBe true

                    /* Server-named, exclusive, and gone with the channel that declared it — so this
                       leaves nothing behind on a broker it shares with everything else here. */
                    val queue = amqp.withChannel { channel -> channel.queueDeclare().queue }
                    queue shouldNotBe ""
                }
            }

            scenario("a borrowed channel is closed when the work is done") {
                /* Borrowing rather than sharing is the whole reason withChannel exists: a channel
                   left open per declare is a channel leak the broker notices before we do. */
                AmqpTestBroker.amqp { amqp ->
                    val borrowed = amqp.withChannel { channel -> channel }
                    borrowed.isOpen shouldBe false
                }
            }

            scenario("closing it closes everything on it") {
                val amqp = AmqpTestBroker.connect()
                val channel = amqp.openChannel()

                amqp.close()

                amqp.isOpen shouldBe false
                channel.isOpen shouldBe false
            }
        }
    })
