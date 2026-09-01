package com.strange.common.concurrent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A loader that parks until a second caller joins it, or gives up waiting.
 *
 * This is what makes the two scenarios below deterministic rather than a race somebody hopes to
 * win: the first caller stays *inside* the loader, so a second caller either gets in — proving the
 * lookup was not atomic — or is made to wait, and the timeout is what tells the two apart.
 */
private class Rendezvous {
    val calls = AtomicInteger()
    val met = CountDownLatch(2)

    fun load(key: String): String {
        calls.incrementAndGet()
        met.countDown()
        met.await(200, TimeUnit.MILLISECONDS)
        return "value-of-$key"
    }
}

private fun bothCall(block: () -> Unit) {
    val threads = List(2) { Thread.ofVirtual().unstarted(block) }
    threads.forEach { it.start() }
    threads.forEach { it.join(5_000) }
}

class MemoTest :
    FeatureSpec({
        feature("the mistake it exists to remove") {
            scenario("the stdlib's getOrPut lets two threads into the same loader") {
                val map = ConcurrentHashMap<String, String>()
                val rendezvous = Rendezvous()

                bothCall { map.getOrPut("k") { rendezvous.load("k") } }

                // Both got in, so both ran the loader: `getOrPut` is a get, a compute and a put with
                // nothing holding them together, whatever map it is called on.
                rendezvous.calls.get() shouldBe 2
                rendezvous.met.count shouldBe 0
            }

            scenario("a Memo does not") {
                val rendezvous = Rendezvous()
                val memo = Memo<String, String> { rendezvous.load(it) }

                bothCall { memo["k"] }

                // The second caller waited on the bin lock and then found the value already there.
                rendezvous.calls.get() shouldBe 1
                memo["k"] shouldBe "value-of-k"
            }
        }

        feature("what it computes") {
            scenario("once per key, however many callers ask") {
                val calls = AtomicInteger()
                val memo =
                    Memo<String, Int> {
                        calls.incrementAndGet()
                        it.length
                    }

                val threads = List(32) { Thread.ofVirtual().unstarted { repeat(50) { memo["abc"] } } }
                threads.forEach { it.start() }
                threads.forEach { it.join(5_000) }

                calls.get() shouldBe 1
                memo["abc"] shouldBe 3
            }

            scenario("a different key is a different computation") {
                val memo = Memo<String, String> { it.uppercase() }
                memo["a"] shouldBe "A"
                memo["b"] shouldBe "B"
                memo.size shouldBe 2
            }
        }

        feature("a loader that fails") {
            scenario("the failure propagates and nothing is kept, so the next caller tries again") {
                val calls = AtomicInteger()
                val memo =
                    Memo<String, String> {
                        if (calls.incrementAndGet() == 1) throw IllegalStateException("not yet") else "ok"
                    }

                shouldThrow<IllegalStateException> { memo["k"] }
                memo.peek("k").shouldBeNull()
                memo["k"] shouldBe "ok"
                calls.get() shouldBe 2
            }
        }

        feature("reading and forgetting") {
            scenario("peek computes nothing") {
                val calls = AtomicInteger()
                val memo = Memo<String, Int> { calls.incrementAndGet() }

                memo.peek("k").shouldBeNull()
                calls.get() shouldBe 0
                memo["k"]
                memo.peek("k") shouldBe 1
            }

            scenario("contains asks about the key, which is not what `in` means on the map underneath") {
                val memo = Memo<String, String> { it }
                memo["k"]

                ("k" in memo) shouldBe true
                // The value, which a ConcurrentHashMap's own `contains` would have answered true to.
                ("nope" in memo) shouldBe false
            }

            scenario("invalidate drops one, clear drops them all") {
                val memo = Memo<String, String> { it.uppercase() }
                memo["a"]
                memo["b"]

                memo.invalidate("a") shouldBe "A"
                memo.peek("a").shouldBeNull()
                memo.size shouldBe 1

                memo.clear()
                memo.size shouldBe 0
            }

            scenario("a snapshot is a copy, not the map") {
                val memo = Memo<String, String> { it.uppercase() }
                memo["a"]
                val snapshot = memo.snapshot()
                memo["b"]

                snapshot shouldBe mapOf("a" to "A")
            }
        }
    })
