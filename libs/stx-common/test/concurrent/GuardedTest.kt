package com.strange.common.concurrent

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Deliberately not thread-safe: a plain `var` incremented with a read and a write. */
private class Tally {
    var count = 0

    fun bump() {
        count += 1
    }
}

class GuardedTest :
    FeatureSpec({
        feature("what the lock buys") {
            scenario("a value that is not thread-safe survives sixteen threads") {
                val guarded = Guarded(Tally())

                val threads =
                    List(16) {
                        Thread.ofVirtual().unstarted {
                            repeat(1_000) { guarded.withLock { tally -> tally.bump() } }
                        }
                    }
                threads.forEach { it.start() }
                threads.forEach { it.join(10_000) }

                guarded.withLock { it.count } shouldBe 16_000
            }

            scenario("the block's result comes back") {
                Guarded(StringBuilder("ab")).withLock { it.reverse().toString() } shouldBe "ba"
            }

            scenario("a failure inside releases the lock rather than keeping it") {
                val guarded = Guarded(Tally())

                runCatching { guarded.withLock { error("boom") } }

                guarded.isHeld shouldBe false
                guarded.withLock { it.count } shouldBe 0
            }
        }

        feature("tryWithLock") {
            scenario("it answers false while somebody else has it, instead of queueing") {
                val guarded = Guarded(Tally())
                val holding = CountDownLatch(1)
                val release = CountDownLatch(1)

                val holder =
                    Thread.ofVirtual().unstarted {
                        guarded.withLock {
                            holding.countDown()
                            release.await(5, TimeUnit.SECONDS)
                        }
                    }
                holder.start()
                holding.await(5, TimeUnit.SECONDS) shouldBe true

                var ran = false
                guarded.tryWithLock { ran = true } shouldBe false
                ran shouldBe false

                release.countDown()
                holder.join(5_000)
                guarded.tryWithLock { ran = true } shouldBe true
                ran shouldBe true
            }
        }

        feature("the lock underneath") {
            scenario("it is reentrant, so a nested call proceeds rather than deadlocking") {
                val guarded = Guarded(Tally())

                guarded.withLock { outer ->
                    outer.bump()
                    guarded.withLock { inner -> inner.bump() }
                }

                guarded.withLock { it.count } shouldBe 2
            }
        }
    })
