package com.softistx.common.concurrent

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ConcurrentMapsTest :
    FeatureSpec({
        feature("getOrCompute") {
            scenario("one loader runs however many callers arrive at a cold key") {
                val map = ConcurrentHashMap<String, Int>()
                val calls = AtomicInteger()

                val threads =
                    List(32) {
                        Thread.ofVirtual().unstarted {
                            repeat(50) { map.getOrCompute("k") { calls.incrementAndGet() } }
                        }
                    }
                threads.forEach { it.start() }
                threads.forEach { it.join(5_000) }

                calls.get() shouldBe 1
                map["k"] shouldBe 1
            }
        }

        feature("update") {
            scenario("the block is handed the absent case as a real null") {
                val map = ConcurrentHashMap<String, Int>()

                map.update("k") { (it ?: 0) + 1 } shouldBe 1
                map.update("k") { (it ?: 0) + 1 } shouldBe 2
                map["k"] shouldBe 2
            }

            scenario("returning null removes the entry, which is the rule the signature hides") {
                val map = ConcurrentHashMap<String, Int>()
                map["k"] = 1

                map.update("k") { null }.shouldBeNull()
                map.containsKey("k") shouldBe false
            }

            scenario("concurrent increments all land, which a get-then-put would not promise") {
                val map = ConcurrentHashMap<String, Int>()

                val threads =
                    List(16) {
                        Thread.ofVirtual().unstarted {
                            repeat(500) { map.update("n") { (it ?: 0) + 1 } }
                        }
                    }
                threads.forEach { it.start() }
                threads.forEach { it.join(10_000) }

                map["n"] shouldBe 8_000
            }
        }
    })
