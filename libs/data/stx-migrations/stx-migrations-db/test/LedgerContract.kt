package com.softistx.migrations.db

import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationStatus
import com.softistx.migrations.ledger.MigrationLedger
import io.kotest.core.spec.style.scopes.FeatureSpecRootScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun record(
    version: Long,
    status: MigrationStatus = MigrationStatus.APPLIED,
    at: Instant = Clock.System.now(),
    description: String = "V$version",
) = MigrationRecord(version, description, status, at, at, appliedBy = "host/1/aaaabbbb")

/** Fresh ledgers over one prepared store — a factory, because two of them are two owners. */
internal typealias Ledgers = () -> MigrationLedger

/**
 * What every `MigrationLedger` has to answer for, asked of each of them.
 *
 * Written once and run three times, which is the reason MongoDB and SQL share a module rather than
 * having one apiece. The runner has one set of rules — a claim is refused rather than overwritten, a
 * stale `RUNNING` record blocks and a fresh one does not, the lock declines rather than queues — and
 * a second ledger that read any of them differently would be a second runner with the same name.
 *
 * [ledgers] hands the block a **factory** over a server the spec's own harness prepared, with the
 * lease set, and cleans up afterwards. A factory rather than one ledger, because the interesting
 * question about a distributed lock is whether a *second owner* is kept out; one instance refusing
 * itself proves much less. Everything a particular store does *beyond* the contract — where the
 * uniqueness comes from, how an instant is written down, what an unrenewed lease does — belongs in
 * that store's own spec, not here.
 */
internal fun FeatureSpecRootScope.ledgerContract(
    label: String,
    enabled: Boolean,
    ledgers: suspend (Duration, suspend (Ledgers) -> Unit) -> Unit,
) {
    feature("the ledger contract, on $label").config(enabled = enabled) {
        scenario("prepare is idempotent, because it runs on every startup") {
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                ledger.prepare()

                ledger.all().shouldBeEmpty()
            }
        }

        scenario("a claimed version is found, and one nobody claimed is not") {
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()

                ledger.claim(record(1)) shouldBe true

                val stored = ledger.find(1L).shouldNotBeNull()
                stored.description shouldBe "V1"
                stored.status shouldBe MigrationStatus.APPLIED
                stored.appliedBy shouldBe "host/1/aaaabbbb"
                ledger.find(2L) shouldBe null
            }
        }

        scenario("claiming a recorded version is refused, and writes nothing over what is there") {
            // The whole reason claim is an insert: whoever got there first is applying it right now,
            // and a second writer would erase the record of that.
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                ledger.claim(record(1, MigrationStatus.RUNNING))

                ledger.claim(record(1, MigrationStatus.FAILED, description = "rival")) shouldBe false

                val stored = ledger.find(1L).shouldNotBeNull()
                stored.status shouldBe MigrationStatus.RUNNING
                stored.description shouldBe "V1"
            }
        }

        scenario("a second ledger over the same store is refused the same claim") {
            ledgers(1.minutes) { open ->
                val first = open()
                val second = open()
                first.prepare()

                first.claim(record(1, MigrationStatus.RUNNING)) shouldBe true
                second.claim(record(1, MigrationStatus.RUNNING)) shouldBe false
            }
        }

        scenario("update moves the record the claim put there") {
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                val claimed = record(1, MigrationStatus.RUNNING)
                ledger.claim(claimed)

                ledger.update(claimed.copy(status = MigrationStatus.APPLIED, durationMillis = 42L))

                val stored = ledger.find(1L).shouldNotBeNull()
                stored.status shouldBe MigrationStatus.APPLIED
                stored.durationMillis shouldBe 42L
            }
        }

        scenario("all() is lowest version first, whatever order they were claimed in") {
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                listOf(30L, 10L, 20L).forEach { ledger.claim(record(it)) }

                ledger.all().map { it.version } shouldContainExactly listOf(10L, 20L, 30L)
            }
        }

        scenario("nothing blocks a ledger where everything is applied") {
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                ledger.claim(record(1))

                ledger.blocking(15.minutes) shouldBe null
            }
        }

        scenario("a FAILED record blocks, however recent it is") {
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                ledger.claim(record(1, MigrationStatus.FAILED))

                ledger.blocking(15.minutes).shouldNotBeNull().version shouldBe 1L
            }
        }

        scenario("a RUNNING record blocks once it is older than staleAfter, and not before") {
            // The sentinel a killed process leaves. Younger than staleAfter it is a run in flight,
            // and the ledger says so rather than guessing.
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                ledger.claim(record(1, MigrationStatus.RUNNING, at = Clock.System.now() - 1.minutes))

                ledger.blocking(15.minutes) shouldBe null
                ledger.blocking(1.seconds).shouldNotBeNull().version shouldBe 1L
            }
        }

        scenario("the lowest blocking version wins, because that is the one to look at first") {
            ledgers(1.minutes) { open ->
                val ledger = open()
                ledger.prepare()
                ledger.claim(record(5, MigrationStatus.FAILED))
                ledger.claim(record(2, MigrationStatus.FAILED))

                ledger.blocking(15.minutes).shouldNotBeNull().version shouldBe 2L
            }
        }

        scenario("a second owner is told no rather than made to wait, and gets in once the first is done") {
            ledgers(1.minutes) { open ->
                val first = open()
                val second = open()
                first.prepare()
                val inside = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()

                coroutineScope {
                    val held =
                        async {
                            first.guarded {
                                inside.complete(Unit)
                                release.await()
                            }
                        }
                    inside.await()

                    withTimeout(10.seconds) { second.guarded { "second" } } shouldBe null

                    release.complete(Unit)
                    held.await()
                }

                withTimeout(10.seconds) { second.guarded { "afterwards" } } shouldBe "afterwards"
            }
        }

        scenario("the lock is released when the work throws") {
            ledgers(1.minutes) { open ->
                val first = open()
                val second = open()
                first.prepare()

                runCatching { first.guarded { error("the migration failed") } }

                withTimeout(10.seconds) { second.guarded { "afterwards" } } shouldBe "afterwards"
            }
        }

        scenario("the lock is released when the coroutine holding it is cancelled") {
            // A scope dying mid-migration is what a crash looks like, and the release is a suspending
            // write a cancelled coroutine cannot make. Without NonCancellable inside Lease the lock
            // would stay held for a whole lease duration — five minutes of an application refusing to
            // start, on the default.
            ledgers(1.minutes) { open ->
                val first = open()
                val second = open()
                first.prepare()
                val inside = CompletableDeferred<Unit>()
                val scope = CoroutineScope(Dispatchers.Default)

                val job =
                    scope.launch {
                        first.guarded {
                            inside.complete(Unit)
                            delay(10.minutes)
                        }
                    }
                inside.await()
                job.cancel()
                job.join()

                withTimeout(10.seconds) { second.guarded { "afterwards" } } shouldBe "afterwards"
            }
        }
    }
}
