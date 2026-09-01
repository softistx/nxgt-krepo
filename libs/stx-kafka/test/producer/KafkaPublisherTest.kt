package com.softistx.kafka.producer

import com.softistx.kafka.record.headersOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.serialization.StringSerializer

/**
 * What a send does with no broker in the way: the shape of the call, what the record carries, and
 * what a rejected send does to the caller. The broker's own answers are pinned down in
 * [KafkaPublisherIntegrationTest]; these are the parts a real cluster would only make slower to
 * check.
 */
class KafkaPublisherTest :
    FeatureSpec({

        fun publisher(autoComplete: Boolean = true): Pair<KafkaPublisher<String, String>, MockProducer<String, String>> {
            val mock = MockProducer(autoComplete, null, StringSerializer(), StringSerializer())
            return KafkaPublisher(mock) to mock
        }

        feature("sending one record") {
            scenario("it answers with where the record landed, not merely that it went") {
                val (publisher, mock) = publisher()

                val sent = publisher.send("orders", "v1", key = "k1", headers = headersOf("trace-id" to "abc"))

                sent.topic shouldBe "orders"
                sent.offset shouldBe 0L

                val written = mock.history().single()
                written.key() shouldBe "k1"
                written.value() shouldBe "v1"
                written
                    .headers()
                    .lastHeader("trace-id")
                    .value()
                    .decodeToString() shouldBe "abc"
            }

            scenario("a second record to the same topic gets the next offset") {
                val (publisher, _) = publisher()

                publisher.send("orders", "v1").offset shouldBe 0L
                publisher.send("orders", "v2").offset shouldBe 1L
            }

            scenario("a null value is sent as a tombstone rather than refused") {
                val (publisher, mock) = publisher()

                publisher.send("orders", null, key = "k1")

                mock.history().single().value() shouldBe null
            }
        }

        feature("sending many") {
            scenario("every record is acknowledged, and they reach the producer in the order given") {
                val (publisher, mock) = publisher()

                /* The order records enter the accumulator is the order they are written to a
                   partition, so records sharing a key depend on this not being shuffled. */
                val sent = publisher.sendAll((1..50).map { ProducerRecord("orders", "same-key", "v$it") })

                sent.size shouldBe 50
                mock.history().map { it.value() } shouldBe (1..50).map { "v$it" }
            }

            scenario("one refused record fails the call without hiding which one") {
                val (publisher, mock) = publisher()
                mock.sendException = RecordTooLargeException("too big")

                shouldThrow<RecordTooLargeException> {
                    publisher.sendAll(listOf(ProducerRecord("orders", "k1", "v1")))
                }
            }
        }

        feature("a send the broker refuses") {
            scenario("the failure reaches the caller rather than being swallowed") {
                val (publisher, mock) = publisher()
                mock.sendException = RecordTooLargeException("too big")

                shouldThrow<RecordTooLargeException> { publisher.send("orders", "v1") }
            }
        }
    })
