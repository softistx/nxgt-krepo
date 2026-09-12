package com.softistx.common.coroutines

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The lock policy the two stores that have no lock to borrow both run.
 *
 * It is three suspending writes and a watchdog, and every interesting case is about *when* the
 * release happens rather than about the writes: after the block returns, after it throws, and after
 * the coroutine holding it is cancelled. The last is the one that reads like an ordinary `finally`
 * and is not — a cancelled coroutine cannot make a suspending call, so without [kotlinx.coroutines.NonCancellable]
 * the release throws out of the `finally` and the id stays held until the lease expires on its own.
 */
class LeaseTest :
    FeatureSpec({

        /** A lease over an in-memory owner, counting every call the policy makes. */
        class Fake(
            duration: Duration = 1.seconds,
            val taken: Boolean = true,
            val renewed: Boolean = true,
        ) {
            val takes = AtomicInteger()
            val renews = AtomicInteger()
            val releases = AtomicInteger()
            val lease =
                Lease(
                    duration = duration,
                    take = {
                        takes.incrementAndGet()
                        taken
                    },
                    renew = {
                        renews.incrementAndGet()
                        renewed
                    },
                    release = {
                        releases.incrementAndGet()
                    },
                )
        }

        feature("holding the lease") {
            scenario("runs the block and gives the answer back") {
                val fake = Fake()

                fake.lease.guard("id") { "done" } shouldBe "done"

                fake.takes.get() shouldBe 1
                fake.releases.get() shouldBe 1
            }

            scenario("releases when the block throws, and the failure is the caller's") {
                val fake = Fake()

                shouldThrow<IllegalStateException> { fake.lease.guard("id") { error("the migration failed") } }

                fake.releases.get() shouldBe 1
            }

            scenario("releases when the coroutine holding it is cancelled") {
                // The reason NonCancellable is in there. A scope dying mid-step is what a crash looks
                // like, and a release skipped here leaves the id held for a whole lease duration.
                val fake = Fake()
                val started = CompletableDeferred<Unit>()

                val scope = CoroutineScope(Dispatchers.Default)
                val job =
                    scope.launch {
                        fake.lease.guard("id") {
                            started.complete(Unit)
                            delay(10.seconds)
                        }
                    }

                started.await()
                job.cancel()
                job.join()

                fake.releases.get() shouldBe 1
            }
        }

        feature("declining rather than queueing") {
            scenario("a lease somebody else holds answers null and never runs the block") {
                val fake = Fake(taken = false)
                var ran = false

                fake.lease.guard("id") { ran = true } shouldBe null

                ran shouldBe false
            }

            scenario("and releases nothing, because it took nothing") {
                // The `take` check is outside the `try` on purpose: a caller that was refused must not
                // clear the lease the holder is relying on.
                val fake = Fake(taken = false)

                fake.lease.guard("id") { }

                fake.releases.get() shouldBe 0
            }
        }

        feature("renewing while the work runs") {
            scenario("keeps extending it, so the expiry is not a guess about how long the block takes") {
                val fake = Fake(duration = 90.milliseconds)

                withTimeout(10.seconds) { fake.lease.guard("id") { delay(400) } }

                // a third of 90ms across 400ms of work — the exact count is the scheduler's business,
                // that it renewed more than once is the policy's
                fake.renews.get() shouldBeGreaterThanOrEqual 2
            }

            scenario("stops the moment the block returns, so nothing renews a lease that is being released") {
                val fake = Fake(duration = 90.milliseconds)

                fake.lease.guard("id") { delay(200) }
                val afterwards = fake.renews.get()
                delay(300)

                fake.renews.get() shouldBe afterwards
            }

            scenario("gives up when a renewal is refused, and lets the block finish anyway") {
                // Losing the lease mid-flight does not stop the work: the block is already running
                // against the resource, and killing it halfway is worse than finishing it.
                val fake = Fake(duration = 90.milliseconds, renewed = false)

                fake.lease.guard("id") { delay(400) } shouldBe Unit

                fake.renews.get() shouldBe 1
                fake.releases.get() shouldBe 1
            }
        }
    })
