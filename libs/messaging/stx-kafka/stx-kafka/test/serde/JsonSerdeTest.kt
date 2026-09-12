package com.softistx.kafka.serde

import com.softistx.kafka.KafkaValueException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class OrderPlaced(
    val id: String,
    val total: Int = 0,
)

/**
 * The wire form is a contract with other services, not this module's private business — so these
 * pin down what actually goes on the topic, and what happens when what comes back is not it.
 */
class JsonSerdeTest :
    FeatureSpec({

        val topic = "orders"

        feature("a serializable value") {
            scenario("it round-trips as JSON, in UTF-8") {
                val serde = jsonSerde<OrderPlaced>()
                val order = OrderPlaced("o1", total = 42)

                val bytes = serde.serializer.serialize(topic, order)!!

                bytes.decodeToString() shouldContain """"id":"o1""""
                serde.deserializer.deserialize(topic, bytes) shouldBe order
            }

            scenario("a null is a tombstone, and stays one in both directions") {
                val serde = jsonSerde<OrderPlaced>()

                serde.serializer.serialize(topic, null).shouldBeNull()
                serde.deserializer.deserialize(topic, null).shouldBeNull()
            }
        }

        feature("a record this consumer cannot read") {
            scenario("the failure names the topic and the type that was expected") {
                val failure =
                    shouldThrow<KafkaValueException> {
                        jsonSerde<OrderPlaced>().deserializer.deserialize(topic, """{"nope":1}""".encodeToByteArray())
                    }

                failure.message shouldContain "orders"
                failure.message shouldContain "OrderPlaced"
            }
        }

        feature("the Json a serde was built with") {
            scenario("the default tolerates a field a newer producer added") {
                val extra = """{"id":"o1","total":42,"channel":"web"}""".encodeToByteArray()

                jsonSerde<OrderPlaced>().deserializer.deserialize(topic, extra) shouldBe OrderPlaced("o1", 42)
            }

            scenario("a strict one refuses it instead") {
                val extra = """{"id":"o1","total":42,"channel":"web"}""".encodeToByteArray()

                shouldThrow<KafkaValueException> {
                    jsonSerde<OrderPlaced>(Json).deserializer.deserialize(topic, extra)
                }
            }
        }

        feature("the serdes that are not JSON") {
            scenario("a string key is stored as itself, not as a quoted JSON string") {
                KafkaSerde.string.serializer
                    .serialize(topic, "k1")!!
                    .decodeToString() shouldBe "k1"
                KafkaSerde.long.deserializer.deserialize(topic, KafkaSerde.long.serializer.serialize(topic, 7L)) shouldBe 7L
            }
        }
    })
