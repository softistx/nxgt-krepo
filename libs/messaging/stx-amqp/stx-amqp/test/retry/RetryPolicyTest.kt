package com.softistx.amqp.retry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The delays themselves, which need no broker to be wrong.
 *
 * The number worth checking is [RetryPolicy.window]: it is how long a message can be in the retry
 * path before anyone sees it parked, and it is the number an on-call engineer wants and never has.
 */
class RetryPolicyTest :
    FeatureSpec({

        feature("a policy") {
            scenario("the list of delays is the number of attempts") {
                RetryPolicy.Default.attempts shouldBe 3
                RetryPolicy.Default.window shouldBe (5.seconds + 30.seconds + 5.minutes)
            }

            scenario("no delays at all is not a policy") {
                shouldThrow<IllegalArgumentException> { RetryPolicy(emptyList()) }
                shouldThrow<IllegalArgumentException> { RetryPolicy(listOf(0.seconds)) }
            }
        }

        feature("backing off") {
            scenario("each delay is the last one multiplied") {
                RetryPolicy.backoff(attempts = 4, first = 1.seconds, factor = 10.0).delays shouldBe
                    listOf(1.seconds, 10.seconds, 100.seconds, 1000.seconds)
            }

            scenario("the ceiling holds, so a long tail does not become a lost message") {
                RetryPolicy.backoff(attempts = 5, first = 1.minutes, factor = 10.0, max = 1.hours).delays shouldBe
                    listOf(1.minutes, 10.minutes, 1.hours, 1.hours, 1.hours)
            }

            scenario("a fixed policy is the same wait every time") {
                RetryPolicy.fixed(attempts = 2, delay = 30.seconds).delays shouldBe listOf(30.seconds, 30.seconds)
            }
        }
    })
