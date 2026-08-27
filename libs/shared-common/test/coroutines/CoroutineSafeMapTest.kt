package com.strange.common.coroutines

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * The map, and the two things it promises.
 *
 * A test that only put and got things back would pass against a plain `mutableMapOf`. What is worth
 * proving is what the mutex is for: that concurrent callers on real threads do not lose each
 * other's writes, and that a read-then-write done through [CoroutineSafeMap.update] is one step
 * rather than two.
 */
class CoroutineSafeMapTest :
    FeatureSpec({

        feature("many callers at once") {
            scenario("no write is lost, even from a thousand coroutines on real threads") {
                val map = CoroutineSafeMap<Int, String>()

                withContext(Dispatchers.IO) {
                    coroutineScope {
                        (1..1000).map { key -> async { map.put(key, "v$key") } }.awaitAll()
                    }
                }

                map.size() shouldBe 1000
                map.get(500) shouldBe "v500"
            }

            scenario("a read-then-write through update is one step, so nothing is counted twice") {
                /* The same loop with get() then put() is two steps with a gap in the middle, and
                   the gap is where the other 999 coroutines get in. */
                val map = CoroutineSafeMap<String, Int>(mapOf("count" to 0))

                withContext(Dispatchers.IO) {
                    coroutineScope {
                        repeat(1000) {
                            async { map.update { it["count"] = (it["count"] ?: 0) + 1 } }
                        }
                    }
                }

                map.get("count") shouldBe 1000
            }
        }

        feature("filling in a missing value") {
            scenario("the default is inserted once and then read") {
                val map = CoroutineSafeMap<String, String>()
                var built = 0

                map.getOrPut("key") {
                    built++
                    "first"
                } shouldBe "first"
                map.getOrPut("key") {
                    built++
                    "second"
                } shouldBe "first"

                built shouldBe 1
            }
        }

        feature("looking at the whole map") {
            scenario("a snapshot is a copy, so iterating it cannot race a writer") {
                val map = CoroutineSafeMap(mapOf("a" to 1))

                val snapshot = map.snapshot()
                map.put("b", 2)

                snapshot shouldBe mapOf("a" to 1)
                map.snapshot() shouldBe mapOf("a" to 1, "b" to 2)
            }
        }
    })
