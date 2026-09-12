package com.softistx.common.concurrent

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A queue that says when somebody is blocked in `take`, and when that block was interrupted.
 *
 * The scenario about cancellation is the reason this bridge exists at all, so it is asserted rather
 * than assumed: without a way to see the interruption, a leaked thread and a released one look
 * exactly the same from outside.
 */
private class Watched<T : Any> : LinkedBlockingQueue<T>() {
    val waiting = CountDownLatch(1)
    val interrupted = CountDownLatch(1)

    override fun take(): T {
        waiting.countDown()
        try {
            return super.take()
        } catch (stop: InterruptedException) {
            interrupted.countDown()
            throw stop
        }
    }
}

class BlockingQueuesTest :
    FeatureSpec({
        feature("consumeAsFlow") {
            scenario("it emits what a producer thread puts in") {
                val queue = LinkedBlockingQueue<String>()
                Thread.ofVirtual().start { listOf("a", "b", "c").forEach { queue.put(it) } }

                queue.consumeAsFlow().take(3).toList() shouldBe listOf("a", "b", "c")
            }

            scenario("cancelling the collector interrupts the blocked take rather than leaking the thread") {
                val queue = Watched<String>()

                coroutineScope {
                    val job = launch(Dispatchers.Default) { queue.consumeAsFlow().collect { } }
                    queue.waiting.await(5, TimeUnit.SECONDS) shouldBe true
                    job.cancelAndJoin()
                }

                // Without runInterruptible this latch never falls, and the thread sits in take()
                // for the life of the process on a queue nobody will feed again.
                queue.interrupted.await(5, TimeUnit.SECONDS) shouldBe true
            }
        }

        feature("drain") {
            scenario("it takes what is queued now and does not wait for more") {
                val queue = LinkedBlockingQueue(listOf("a", "b"))

                queue.drain() shouldBe listOf("a", "b")
                queue.drain() shouldBe emptyList()
            }

            scenario("the limit is respected and the rest stays queued") {
                val queue = ArrayBlockingQueue(4, false, listOf("a", "b", "c", "d"))

                queue.drain(2) shouldBe listOf("a", "b")
                queue.drain() shouldBe listOf("c", "d")
            }
        }
    })
