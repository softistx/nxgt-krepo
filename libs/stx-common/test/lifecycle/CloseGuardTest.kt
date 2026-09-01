package com.softistx.common.lifecycle

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class CloseGuardTest :
    FeatureSpec({

        feature("a guarded close") {
            scenario("runs the first time and not the second") {
                val guard = CloseGuard()
                val closes = AtomicInteger()

                repeat(5) { guard.once { closes.incrementAndGet() } }

                closes.get() shouldBe 1
            }

            scenario("says so before the block it is running has finished") {
                val guard = CloseGuard()
                var seen = false

                guard.once { seen = guard.isClosed }

                seen shouldBe true
            }

            scenario("runs once even when everything calls it at the same time") {
                val guard = CloseGuard()
                val closes = AtomicInteger()

                // A shutdown hook, a `use` block and a container's teardown are three threads with
                // an equal claim, so the guard has to be a compare-and-set rather than a read-then-write.
                withContext(Dispatchers.Default) {
                    coroutineScope {
                        (1..64).map { async { guard.once { closes.incrementAndGet() } } }.awaitAll()
                    }
                }

                closes.get() shouldBe 1
            }
        }
    })
