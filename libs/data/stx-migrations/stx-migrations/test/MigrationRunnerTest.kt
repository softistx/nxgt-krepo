package com.softistx.migrations

import com.softistx.migrations.ledger.InMemoryLedger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The half of this library that has no store in it: the order, the gate, and what a run refuses.
 *
 * Every scenario here runs against [InMemoryLedger] and finishes in milliseconds. That is the reason
 * the core is a module of its own — merged into `stx-migrations-db`, each of these would be red on
 * any machine without a container running.
 */
class MigrationRunnerTest :
    FeatureSpec({

        /** What ran, in the order it ran. */
        val applied = mutableListOf<String>()

        /** A migration over the trivial context — the runner's rules have nothing to do with the store. */
        fun migration(
            at: Long,
            name: String = "V$at",
            body: suspend () -> Unit = { applied += name },
        ) = object : Migration<Unit> {
            override val version = at
            override val description = name

            override suspend fun migrate(context: Unit) = body()
        }

        fun runner(
            ledger: InMemoryLedger,
            vararg migrations: Migration<Unit>,
            lockTimeout: Duration = 5.minutes,
            lockPoll: Duration = 10.milliseconds,
            staleAfter: Duration = 15.minutes,
        ) = MigrationRunner(
            ledger = ledger,
            migrations = migrations.toList(),
            context = MigrationContext { it(Unit) },
            lockTimeout = lockTimeout,
            lockPoll = lockPoll,
            staleAfter = staleAfter,
        )

        beforeTest { applied.clear() }

        feature("applying what is outstanding") {
            scenario("runs them lowest version first, whatever order they were handed over in") {
                val ledger = InMemoryLedger()

                val records = runner(ledger, migration(3), migration(1), migration(2)).run()

                applied shouldContainExactly listOf("V1", "V2", "V3")
                records.map { it.version } shouldContainExactly listOf(1L, 2L, 3L)
                records.map { it.status } shouldContainExactly List(3) { MigrationStatus.APPLIED }
            }

            scenario("does not run an applied one again") {
                val ledger = InMemoryLedger()
                runner(ledger, migration(1)).run()
                applied.clear()

                runner(ledger, migration(1), migration(2)).run()

                applied shouldContainExactly listOf("V2")
            }

            scenario("records who applied it and how long it took") {
                val ledger = InMemoryLedger()

                runner(ledger, migration(1) { delay(20) }).run()

                val record = ledger.find(1L).shouldNotBeNull()
                record.appliedBy.shouldNotBeNull()
                record.durationMillis.shouldNotBeNull() shouldBeGreaterThanOrEqual 15L
            }

            scenario("takes the description from the class name when the migration does not give one") {
                val ledger = InMemoryLedger()

                val unnamed =
                    object : Migration<Unit> {
                        override val version = 7L

                        override suspend fun migrate(context: Unit) = Unit
                    }
                runner(ledger, unnamed).run()

                // an anonymous object has no simple name of its own; what matters is that the default
                // is the class rather than a blank the ledger would carry forever
                ledger.find(7L).shouldNotBeNull().description shouldBe unnamed::class.java.simpleName
            }

            scenario("with nothing to run, writes nothing and answers with the ledger as it stands") {
                val ledger = InMemoryLedger()

                runner(ledger).run().shouldBeEmpty()

                ledger.all().shouldBeEmpty()
            }
        }

        feature("refusing to run") {
            scenario("two migrations at one version are refused when the runner is built, not when it runs") {
                val ledger = InMemoryLedger()

                val failure =
                    shouldThrow<DuplicateMigrationVersionException> {
                        runner(ledger, migration(1, "V1Orders"), migration(1, "V1Rival"))
                    }

                failure.message shouldContain "V1Orders"
                failure.message shouldContain "V1Rival"
                applied.shouldBeEmpty()
                ledger.all().shouldBeEmpty()
            }

            scenario("a failure is recorded, leaves the run, and stops everything after it") {
                val ledger = InMemoryLedger()

                val failure =
                    shouldThrow<MigrationFailedException> {
                        runner(ledger, migration(1), migration(2) { error("no such column") }, migration(3)).run()
                    }

                failure.version shouldBe 2L
                failure.message shouldContain "no such column"
                applied shouldContainExactly listOf("V1")
                ledger.find(2L).shouldNotBeNull().status shouldBe MigrationStatus.FAILED
                ledger.find(3L) shouldBe null
            }

            scenario("a later run refuses to do anything at all while a failure stands") {
                // Not "skips the failed one and runs the rest": the ones after it were written against
                // a change that did not happen.
                val ledger = InMemoryLedger()
                shouldThrow<MigrationFailedException> { runner(ledger, migration(1) { error("no") }).run() }
                applied.clear()

                val halted =
                    shouldThrow<MigrationHaltedException> { runner(ledger, migration(1), migration(2)).run() }

                halted.record.version shouldBe 1L
                applied.shouldBeEmpty()
            }

            scenario("a RUNNING record older than staleAfter halts the run, naming the version") {
                // What a process killed mid-migration leaves. The alternative — treating it as never
                // attempted — is the silent re-application this library exists to remove.
                val ledger = InMemoryLedger()
                val longAgo = Clock.System.now() - 1.minutes
                ledger.claim(
                    MigrationRecord(4L, "V4Backfill", MigrationStatus.RUNNING, longAgo, longAgo, appliedBy = "host/1/dead"),
                )

                val halted =
                    shouldThrow<MigrationHaltedException> {
                        runner(ledger, migration(4), staleAfter = 1.seconds).run()
                    }

                halted.record.version shouldBe 4L
                halted.message shouldContain "host/1/dead"
                applied.shouldBeEmpty()
            }

            scenario("a RUNNING record younger than staleAfter does not, because that is a run in flight") {
                val ledger = InMemoryLedger()
                val now = Clock.System.now()
                ledger.claim(MigrationRecord(4L, "V4Backfill", MigrationStatus.RUNNING, now, now))

                // it is not APPLIED, so this run claims it — and the claim is what fails, which is the
                // right failure: two processes both believe they hold the lock
                shouldThrow<MigrationConflictException> {
                    runner(ledger, migration(4), staleAfter = 10.minutes).run()
                }
            }
        }

        feature("the lock") {
            scenario("a second run waits for the first rather than starting beside it") {
                val ledger = InMemoryLedger()
                val inside = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()

                coroutineScope {
                    val first =
                        async {
                            runner(
                                ledger,
                                migration(1) {
                                    inside.complete(Unit)
                                    release.await()
                                    applied += "V1"
                                },
                            ).run()
                        }
                    inside.await()

                    val second = async { runner(ledger, migration(1), migration(2)).run() }
                    delay(50)
                    release.complete(Unit)

                    first.await()
                    second.await()
                }

                // the second run found V1 applied and did only V2 — it did not run V1 a second time
                applied shouldContainExactly listOf("V1", "V2")
            }

            scenario("gives up rather than serving against an unmigrated schema") {
                val ledger = InMemoryLedger()
                val inside = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()

                coroutineScope {
                    val first =
                        async {
                            runner(
                                ledger,
                                migration(1) {
                                    inside.complete(Unit)
                                    release.await()
                                },
                            ).run()
                        }
                    inside.await()

                    shouldThrow<MigrationLockTimeoutException> {
                        runner(ledger, migration(2), lockTimeout = 100.milliseconds).run()
                    }

                    release.complete(Unit)
                    first.await()
                }
            }

            scenario("is released when a migration throws, so the next process is not locked out too") {
                val ledger = InMemoryLedger()
                shouldThrow<MigrationFailedException> { runner(ledger, migration(1) { error("no") }).run() }

                // the lock is free: this run gets far enough to reach the halt check
                shouldThrow<MigrationHaltedException> {
                    runner(ledger, migration(1), lockTimeout = 100.milliseconds).run()
                }
            }
        }
    })
