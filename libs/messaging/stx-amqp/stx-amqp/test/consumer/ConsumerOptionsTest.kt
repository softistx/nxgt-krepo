package com.softistx.amqp.consumer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The prefetch rule, which needs no broker: see `DeliveryThreadsTest` for why the limit exists. */
class ConsumerOptionsTest :
    FunSpec({

        test("a prefetch up to the delivery buffer is accepted") {
            ConsumerOptions(prefetch = 1).prefetch shouldBe 1
            ConsumerOptions(prefetch = ConsumerOptions.MAX_PREFETCH).prefetch shouldBe 64
        }

        test("a prefetch above the buffer is refused, and the message gives the limit") {
            val refusal = shouldThrow<IllegalArgumentException> { ConsumerOptions(prefetch = 65) }

            refusal.message shouldContain "between 1 and 64"
            refusal.message shouldContain "not 65"
        }

        test("no limit at all is refused too, since the broker would then push the whole queue") {
            val refusal = shouldThrow<IllegalArgumentException> { ConsumerOptions(prefetch = 0) }
            refusal.message shouldContain "0 means no limit"
            shouldThrow<IllegalArgumentException> { ConsumerOptions(prefetch = -1) }
        }
    })
