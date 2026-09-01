package com.softistx.amqp.message

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.impl.LongStringHelper
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The headers, and the cast that would otherwise be waiting in them.
 *
 * A string header does not arrive as a `String` — the client wraps it in a `LongString`, so the
 * obvious cast compiles, reads correctly, and throws at runtime on the one header somebody added
 * last. That is the whole reason this type exists, so it is the first thing proven here.
 */
class MessageHeadersTest :
    FeatureSpec({

        feature("reading a header") {
            scenario("a string arrives as a LongString and comes back as text") {
                val headers = MessageHeaders(mapOf("tenant" to LongStringHelper.asLongString("acme")))

                headers.string("tenant") shouldBe "acme"
                (headers["tenant"] is String) shouldBe false
            }

            scenario("numbers keep their type, whichever width the publisher used") {
                val headers = MessageHeaders(mapOf("attempt" to 2, "size" to 9_000_000_000L))

                headers.int("attempt") shouldBe 2
                headers.long("attempt") shouldBe 2L
                headers.long("size") shouldBe 9_000_000_000L
            }

            scenario("a header nobody set is null rather than an exception") {
                MessageHeaders().string("missing") shouldBe null
                ("missing" in MessageHeaders()) shouldBe false
            }
        }

        feature("publishing headers") {
            scenario("nulls are dropped, because AMQP has no null header") {
                val headers = headersOf("tenant" to "acme", "trace" to null)

                headers.asPublished() shouldBe mapOf<String, Any>("tenant" to "acme")
            }
        }

        feature("how often a message has been dead-lettered") {
            scenario("the count comes off the header the broker maintains") {
                /* Three casts deep into the client's own types, and the number every retry loop is
                   really about. */
                val properties =
                    AMQP.BasicProperties
                        .Builder()
                        .headers(mapOf("x-death" to listOf(mapOf("count" to 3L, "reason" to "rejected"))))
                        .build()

                message(properties).deathCount shouldBe 3L
            }

            scenario("a message that has never been dead-lettered counts zero") {
                message(AMQP.BasicProperties.Builder().build()).deathCount shouldBe 0L
            }
        }
    })

private fun message(properties: AMQP.BasicProperties) =
    AmqpMessage(
        body = "body",
        exchange = "orders",
        routingKey = "order.placed",
        deliveryTag = 1,
        redelivered = false,
        headers = MessageHeaders(properties.headers.orEmpty()),
        properties = properties,
    )
