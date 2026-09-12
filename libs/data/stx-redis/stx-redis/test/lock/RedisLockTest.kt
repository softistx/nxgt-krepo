package com.softistx.redis.lock

import com.softistx.redis.RedisLockException
import com.softistx.redis.RedisTestServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RedisLockTest :
    FeatureSpec({

        feature("holding a lock").config(enabled = RedisTestServer.available) {
            scenario("the first caller gets a token and the second gets nothing") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "invoice:42")

                    val token = lock.tryAcquire()
                    token shouldNotBe null
                    lock.tryAcquire() shouldBe null
                    lock.isHeld() shouldBe true

                    lock.release(token!!) shouldBe true
                    lock.isHeld() shouldBe false
                }
            }

            scenario("a lock cannot be released by somebody who does not hold it") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "invoice:42")
                    lock.tryAcquire() shouldNotBe null

                    lock.release("some-other-token") shouldBe false

                    // The point of the Lua: the wrong token did not free the right lock.
                    lock.isHeld() shouldBe true
                }
            }

            scenario("it lets go on its own, so a holder that dies is not a deadlock") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "invoice:42", ttl = 200.milliseconds)
                    lock.tryAcquire() shouldNotBe null

                    delay(400)

                    lock.tryAcquire() shouldNotBe null
                }
            }
        }

        feature("withLock").config(enabled = RedisTestServer.available) {
            scenario("the lock is released however the block ends") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "invoice:42")

                    lock.withLock { "done" } shouldBe "done"
                    lock.isHeld() shouldBe false

                    shouldThrow<IllegalStateException> { lock.withLock { error("no") } }
                    lock.isHeld() shouldBe false
                }
            }

            scenario("a waiter gets in once the holder is done") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "invoice:42")

                    val results =
                        coroutineScope {
                            val first =
                                async {
                                    lock.withLock {
                                        delay(200)
                                        "first"
                                    }
                                }
                            delay(50)
                            val second = async { lock.withLock(wait = 2.seconds) { "second" } }
                            listOf(first, second).awaitAll()
                        }

                    results shouldBe listOf("first", "second")
                }
            }

            scenario("a wait that runs out is an exception, or a null if the caller prefers") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "invoice:42")
                    lock.tryAcquire() shouldNotBe null

                    shouldThrow<RedisLockException> { lock.withLock(wait = 100.milliseconds) { "never" } }
                    lock.withLockOrNull(wait = 100.milliseconds) { "never" } shouldBe null
                }
            }

            scenario("only one caller at a time is inside the block") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "counter")
                    val inside = AtomicInteger()
                    val overlaps = AtomicInteger()

                    coroutineScope {
                        List(8) {
                            async {
                                lock.withLock(wait = 5.seconds) {
                                    if (inside.incrementAndGet() > 1) overlaps.incrementAndGet()
                                    delay(20)
                                    inside.decrementAndGet()
                                }
                            }
                        }.awaitAll()
                    }

                    overlaps.get() shouldBe 0
                }
            }
        }

        feature("work that outlives the TTL").config(enabled = RedisTestServer.available) {
            scenario("the watchdog keeps the lock while the block is still running") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "slow", ttl = 300.milliseconds)

                    coroutineScope {
                        val held =
                            async {
                                lock.withLock {
                                    delay(900)
                                    "done"
                                }
                            }
                        delay(600)

                        // Twice the TTL in, and the lock is still ours.
                        lock.tryAcquire() shouldBe null
                        held.await() shouldBe "done"
                    }

                    lock.isHeld() shouldBe false
                }
            }

            scenario("without the watchdog the lock expires under the work, as asked") {
                RedisTestServer.withRedis { redis ->
                    val lock = RedisLock(redis, "slow", ttl = 200.milliseconds)

                    coroutineScope {
                        val held =
                            async {
                                lock.withLock(renew = false) {
                                    delay(600)
                                    "done"
                                }
                            }
                        delay(400)

                        lock.tryAcquire() shouldNotBe null
                        held.await() shouldBe "done"
                    }
                }
            }
        }
    })
