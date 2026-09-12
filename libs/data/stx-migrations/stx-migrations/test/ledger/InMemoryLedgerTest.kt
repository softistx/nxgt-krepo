package com.softistx.migrations.ledger

import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationStatus
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private fun record(
    version: Long,
    status: MigrationStatus = MigrationStatus.APPLIED,
    at: Instant = Clock.System.now(),
) = MigrationRecord(version, "V$version", status, at, at)

/**
 * The reference implementation, held to the contract every ledger answers.
 *
 * The store-backed ones are asked the same questions by `LedgerContract` in `stx-migrations-db`,
 * against a real server. This is the copy that runs everywhere, including on a machine with no
 * Docker — which is what makes it worth having twice.
 */
class InMemoryLedgerTest :
    FeatureSpec({

        feature("claiming a version") {
            scenario("a claimed record is found, and one nobody claimed is not") {
                val ledger = InMemoryLedger()

                ledger.claim(record(1)) shouldBe true

                ledger.find(1L).shouldNotBeNull().description shouldBe "V1"
                ledger.find(2L) shouldBe null
            }

            scenario("claiming a version twice is refused, and does not overwrite what is there") {
                val ledger = InMemoryLedger()
                ledger.claim(record(1, MigrationStatus.APPLIED))

                ledger.claim(record(1, MigrationStatus.FAILED).copy(description = "rival")) shouldBe false

                val stored = ledger.find(1L).shouldNotBeNull()
                stored.status shouldBe MigrationStatus.APPLIED
                stored.description shouldBe "V1"
            }

            scenario("update overwrites the claim, which is the only thing allowed to") {
                val ledger = InMemoryLedger()
                ledger.claim(record(1, MigrationStatus.RUNNING))

                ledger.update(record(1, MigrationStatus.APPLIED).copy(durationMillis = 42L))

                ledger.find(1L).shouldNotBeNull().durationMillis shouldBe 42L
            }
        }

        feature("reading the ledger") {
            scenario("all() is lowest version first, whatever order they went in") {
                val ledger = InMemoryLedger()
                listOf(3L, 1L, 2L).forEach { ledger.claim(record(it)) }

                ledger.all().map { it.version } shouldContainExactly listOf(1L, 2L, 3L)
            }

            scenario("nothing blocks a clean ledger") {
                val ledger = InMemoryLedger()
                ledger.claim(record(1))

                ledger.blocking(15.minutes) shouldBe null
            }

            scenario("a FAILED record blocks, however old it is") {
                val ledger = InMemoryLedger()
                ledger.claim(record(1, MigrationStatus.FAILED))

                ledger.blocking(15.minutes).shouldNotBeNull().version shouldBe 1L
            }

            scenario("a RUNNING record blocks once it is older than staleAfter, and not before") {
                val ledger = InMemoryLedger()
                ledger.claim(record(1, MigrationStatus.RUNNING, at = Clock.System.now() - 1.minutes))

                ledger.blocking(15.minutes) shouldBe null
                ledger.blocking(1.seconds).shouldNotBeNull().version shouldBe 1L
            }

            scenario("the lowest blocking version wins, because that is the one to look at first") {
                val ledger = InMemoryLedger()
                ledger.claim(record(5, MigrationStatus.FAILED))
                ledger.claim(record(2, MigrationStatus.FAILED))

                ledger.blocking(15.minutes).shouldNotBeNull().version shouldBe 2L
            }

            scenario("a fresh ledger holds nothing and prepare() changes that not at all") {
                val ledger = InMemoryLedger()

                ledger.prepare()
                ledger.prepare()

                ledger.all().shouldBeEmpty()
            }
        }

        feature("the lock") {
            scenario("a second holder is told no rather than made to wait") {
                val ledger = InMemoryLedger()
                val inside = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()

                coroutineScope {
                    val held =
                        async {
                            ledger.guarded {
                                inside.complete(Unit)
                                release.await()
                            }
                        }
                    inside.await()

                    ledger.guarded { "second" } shouldBe null

                    release.complete(Unit)
                    held.await()
                }

                ledger.guarded { "afterwards" } shouldBe "afterwards"
            }

            scenario("is released when the work throws") {
                val ledger = InMemoryLedger()

                runCatching { ledger.guarded { error("the migration failed") } }

                ledger.guarded { "afterwards" } shouldBe "afterwards"
            }
        }
    })
