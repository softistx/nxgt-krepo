package com.softistx.amqp.codec

import com.softistx.amqp.AmqpValueException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a body is on the wire.
 *
 * The interesting cases are the two that decide whether a rolling deploy survives: a consumer
 * reading a message written by a newer publisher, and a consumer reading one it cannot parse at all.
 */
class AmqpCodecTest :
    FeatureSpec({

        feature("JSON bodies") {
            scenario("a serializable type goes out and comes back") {
                val codec = jsonCodec<Order>()

                codec.decode(codec.encode(Order("A1", 2))) shouldBe Order("A1", 2)
                codec.contentType shouldBe "application/json"
            }

            scenario("the default tolerates a field a newer publisher added") {
                /* The alternative is a queue that dead-letters everything for as long as a deploy
                   is half-done. */
                val decoded = jsonCodec<Order>().decode("""{"id":"A1","quantity":2,"promoCode":"NEW"}""".toByteArray())

                decoded shouldBe Order("A1", 2)
            }

            scenario("a strict Json refuses it instead, and the codec says which type failed") {
                val strict = jsonCodec<Order>(Json)

                val failure =
                    shouldThrow<AmqpValueException> {
                        strict.decode("""{"id":"A1","quantity":2,"promoCode":"NEW"}""".toByteArray())
                    }

                failure.message!! shouldContain "Order"
            }

            scenario("the failure does not repeat the body, whatever was in it") {
                /* A body is somebody's payment details as often as it is a test fixture, and an
                   exception message ends up in a log nobody meant to make sensitive. */
                val failure = shouldThrow<AmqpValueException> { jsonCodec<Order>().decode("""{"id":4111111111111111}""".toByteArray()) }

                failure.message!! shouldNotContain "4111111111111111"
            }
        }

        feature("bodies that are not JSON") {
            scenario("text is itself, not a quoted JSON string") {
                AmqpCodec.text.encode("plain").decodeToString() shouldBe "plain"
                AmqpCodec.text.contentType shouldBe "text/plain"
            }

            scenario("bytes pass straight through") {
                val bytes = byteArrayOf(1, 2, 3)

                AmqpCodec.bytes.encode(bytes) shouldBe bytes
                AmqpCodec.bytes.contentType shouldBe "application/octet-stream"
            }
        }
    })

@Serializable
private data class Order(
    val id: String,
    val quantity: Int,
)
