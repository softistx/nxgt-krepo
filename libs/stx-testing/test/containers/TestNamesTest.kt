package com.strange.testing.containers

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith

/**
 * That a name is unique twice over — within a run, and against the run before it.
 *
 * The second half is the one worth a spec: it is what replaced a sweep that could not tell a crashed
 * run's leftovers from a concurrent run's.
 */
class TestNamesTest :
    FeatureSpec({

        feature("naming a namespace") {
            scenario("each call answers something new") {
                val names = TestNames("stx-test")

                val issued = List(100) { names.next() }

                issued.toSet() shouldHaveSize 100
            }

            scenario("the prefix and separator are the caller's") {
                TestNames("stx_mongo_test", separator = "_").next() shouldStartWith "stx_mongo_test_1_"
                TestNames("stx-redis-test", separator = ":").next() shouldStartWith "stx-redis-test:1:"
            }

            scenario("counters are per instance, so two namespaces do not share one") {
                TestNames("a").next() shouldBe TestNames("a").next()
            }
        }

        feature("surviving a run that did not finish") {
            scenario("two runs issue different first names, where a bare counter would repeat 1") {
                // The whole point: `stx_spring_1` from a crashed run is what the next run collided
                // with, and what the sweep in SpringMongo existed to clear.
                val first = TestNames("stx-test").next()

                first.substringAfterLast('-') shouldNotBe ""
                first shouldMatch Regex("stx-test-1-[a-z0-9]{1,8}")
            }

            scenario("the suffix is legal in a bucket, an SQL identifier and a Mongo database alike") {
                // Lowercase alphanumeric only: a bucket name refuses `_`, and none of the three
                // accepts the `/` or `.` a UUID-free encoding might otherwise produce.
                TestNames("stx-test").next().substringAfterLast('-') shouldMatch Regex("[a-z0-9]+")
            }

            scenario("a Mongo database name stays inside its 63 bytes") {
                TestNames("stx_spring_boot_test", separator = "_").next().length shouldBe 20 + 1 + 1 + 1 + 8
            }
        }
    })
