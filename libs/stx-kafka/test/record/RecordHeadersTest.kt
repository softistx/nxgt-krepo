package com.strange.kafka.record

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Headers are the one part of a record where the convenient shape and the true shape differ, so
 * these hold both: a map view for the common case, and the repeated names it cannot express.
 */
class RecordHeadersTest :
    FeatureSpec({

        feature("the map view") {
            scenario("a name reads back, and one that was never set is null") {
                val headers = headersOf("trace-id" to "abc", "source" to "billing")

                headers["trace-id"] shouldBe "abc"
                headers["absent"].shouldBeNull()
                ("source" in headers) shouldBe true
                headers.toMap() shouldBe mapOf("trace-id" to "abc", "source" to "billing")
            }
        }

        feature("a name that appears more than once") {
            scenario("every value is kept, and the last one is what the map view reports") {
                /* A retry count and a trace context accumulate down a chain of services; a map
                   would silently drop all but one of them. */
                val headers = headersOf("hop" to "a") + ("hop" to "b")

                headers.all("hop") shouldBe listOf("a", "b")
                headers["hop"] shouldBe "b"
            }
        }

        feature("the round trip through Kafka's own headers") {
            scenario("names, values and repeats all survive it") {
                val headers = headersOf("hop" to "a", "trace-id" to "abc") + ("hop" to "b")

                RecordHeaders.from(headers.toKafka()) shouldBe headers
            }
        }
    })
