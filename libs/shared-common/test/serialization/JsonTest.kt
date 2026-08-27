package com.strange.common.serialization

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * The one `Json` the storage and messaging libraries read through, and the decode that names the
 * failure for them.
 *
 * The leniency is the whole reason it exists, so it is the first thing proven — and the failure
 * path is proven not to quote the value, because that is the part that is easy to regress and
 * expensive when it happens.
 */
class JsonTest :
    FeatureSpec({

        feature("reading what somebody else wrote") {
            scenario("a field a newer writer added is ignored rather than fatal") {
                val decoded = lenientJson.decodeFromString<Order>("""{"id":"A1","promoCode":"NEW"}""")

                decoded shouldBe Order("A1")
            }

            scenario("a strict Json is the one that refuses it — so the default is a choice") {
                shouldThrow<Exception> { Json.decodeFromString<Order>("""{"id":"A1","promoCode":"NEW"}""") }
            }
        }

        feature("decoding on behalf of a layer") {
            scenario("the layer's own exception is what comes out, carrying its own context") {
                val failure =
                    shouldThrow<IllegalStateException> {
                        lenientJson.decodeValue(serializer<Order>(), """{"id":4}""") {
                            IllegalStateException("the value at key 'orders:A1' is not an ${serializer<Order>().typeName}")
                        }
                    }

                failure.message!! shouldContain "orders:A1"
                failure.message!! shouldContain "order"
            }

            scenario("nothing hands the value itself to the failure") {
                /* A stored value is somebody's payment details as often as it is a fixture. The
                   signature does not offer it, and this is the spec that keeps it that way. */
                val failure =
                    shouldThrow<IllegalStateException> {
                        lenientJson.decodeValue(serializer<Order>(), """{"id":4111111111111111}""") {
                            IllegalStateException("not an ${serializer<Order>().typeName}")
                        }
                    }

                failure.message!! shouldNotContain "4111111111111111"
            }

            scenario("a value that decodes is simply returned") {
                lenientJson.decodeValue(serializer<Order>(), """{"id":"A1"}""") { error("not reached") } shouldBe Order("A1")
            }
        }

        feature("naming a type in a failure") {
            scenario("it is the serial name, so a @SerialName is respected") {
                serializer<Order>().typeName shouldBe "order"
            }
        }
    })

@Serializable
@SerialName("order")
private data class Order(
    val id: String,
)
