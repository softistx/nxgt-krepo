package com.strange.common.coroutines

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.TimeSource

/**
 * One lock per key — which is only worth having if both halves are true.
 *
 * Same key must exclude, or it is not a lock. Different keys must *not*, or it is a single lock
 * with extra steps and the stampede it was meant to prevent is now a queue.
 */
class KeyedMutexTest :
    FeatureSpec({

        feature("callers wanting the same key") {
            scenario("they take turns rather than overlapping") {
                val locks = KeyedMutex<String>()
                val inside = AtomicInteger()
                var overlapped = false

                coroutineScope {
                    (1..8)
                        .map {
                            async {
                                locks.withLock("same") {
                                    if (inside.incrementAndGet() > 1) overlapped = true
                                    delay(20)
                                    inside.decrementAndGet()
                                }
                            }
                        }.awaitAll()
                }

                overlapped shouldBe false
            }

            scenario("the second caller sees what the first one did") {
                // The double-check the KDoc asks for: whoever waits finds the work already done.
                val locks = KeyedMutex<String>()
                val cache = CoroutineSafeMap<String, String>()
                val loads = AtomicInteger()

                suspend fun load(key: String): String =
                    cache.get(key) ?: locks.withLock(key) {
                        cache.get(key) ?: "loaded".also {
                            loads.incrementAndGet()
                            delay(50)
                            cache.put(key, it)
                        }
                    }

                coroutineScope { (1..5).map { async { load("key") } }.awaitAll() }

                loads.get() shouldBe 1
            }
        }

        feature("callers wanting different keys") {
            scenario("they do not wait for each other") {
                val locks = KeyedMutex<Int>()
                val started = TimeSource.Monotonic.markNow()

                coroutineScope {
                    (1..4).map { key -> async { locks.withLock(key) { delay(200) } } }.awaitAll()
                }

                // Four keys, 200ms each: one lock for all of them would be 800ms.
                started.elapsedNow().inWholeMilliseconds shouldBeGreaterThanOrEqual 200L
                started.elapsedNow().inWholeMilliseconds shouldBeLessThan 600L
            }
        }

        feature("what is left behind") {
            scenario("a key's lock goes away with the last caller who wanted it") {
                // Otherwise this is a map of every key the process has ever seen.
                val locks = KeyedMutex<String>()

                coroutineScope { (1..50).map { async { locks.withLock("key$it") { delay(5) } } }.awaitAll() }

                locks.held() shouldBe 0
            }
        }
    })
