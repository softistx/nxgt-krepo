package com.softistx.graphix

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * The two runtime facts `maxListElements` rests on, measured on their own.
 *
 * Written before the design that uses them, the way `ExceptionSeatTest` was. `StreamListTest` proves
 * the feature; this proves the mechanism underneath it, so a failure says *which* of the two broke.
 */
class FlowSeatTest :
    FeatureSpec({
        feature("bounding a flow") {
            scenario("take ends an infinite source rather than collecting it") {
                val emitted = AtomicInteger()
                val endless =
                    flow {
                        var index = 0
                        while (true) {
                            emitted.incrementAndGet()
                            emit(index++)
                        }
                    }

                // The bound is `take(max + 1).toList()` and not a throw from inside `collect`:
                // `take` aborts the upstream with kotlinx's own mechanism, which an ill-written
                // `flow { }` catching around its `emit` cannot swallow. The timeout is the
                // assertion — a regression must fail this spec, not wedge the suite.
                withTimeout(5_000) { endless.take(4).toList() shouldBe listOf(0, 1, 2, 3) }
                emitted.get() shouldBe 4
            }

            scenario("a source shorter than the bound is not padded, so 'exactly max' stays legible") {
                // `take(max + 1)` is what lets a list of exactly `max` elements be told apart from
                // one that had more, without collecting the rest of an infinite flow to find out.
                withTimeout(5_000) {
                    flow {
                        emit(1)
                        emit(2)
                    }.take(4).toList() shouldBe listOf(1, 2)
                }
            }
        }
    })
