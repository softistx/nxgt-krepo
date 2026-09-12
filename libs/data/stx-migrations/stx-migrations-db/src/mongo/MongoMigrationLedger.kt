package com.softistx.migrations.db.mongo

import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.common.coroutines.Lease
import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationStatus
import com.softistx.migrations.ledger.MigrationLedger
import com.softistx.migrations.migrationIdentity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.bson.Document
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The one document in the lock collection. A name rather than a number, so a `find()` reads. */
private const val LOCK = "migrations"

/**
 * The ledger in MongoDB: one document per version, and one more holding the lock.
 *
 * **Two collections, not one.** A lock is not a migration, and a sentinel document living beside the
 * records is one `find()` away from being read as a version that has run — the same argument the SQL
 * ledger makes for a second table.
 *
 * **The version is the `_id`.** That is the faithful translation of `version bigint primary key`, and
 * it is what makes [claim] an ordinary `insertOne` whose duplicate-key error *is* the answer. There
 * is no secondary index to create, so nothing here can collide with an index somebody else declared
 * — which is the failure `MigrationStore.prepare()` in the runner this replaces had to name its index
 * to avoid.
 *
 * **Raw [Document], not a `@Serializable` type.** `stx-telemetry-mongo` writes its instants back as
 * BSON dates for the same reason — *"JSON has no date, and a string is not something Mongo will
 * expire or index"* — and the payoff is larger here: a ledger over plain documents works with **any**
 * [MongoDatabase] whatever codec registry it carries, which is what lets a Spring Data application
 * reuse the pool it already has instead of opening a second one.
 *
 * **No replica set is needed.** `withTransaction` is what needs one, and nothing here uses it: every
 * guarantee this ledger makes comes from single-document atomicity.
 *
 * It closes nothing. The application that opened the client is the one that closes it.
 */
class MongoMigrationLedger(
    database: MongoDatabase,
    collection: String = "stx_migrations",
    /**
     * How long the lock is held before it is considered abandoned.
     *
     * Renewed at a third of this while a migration runs, so it expires only when the process holding
     * it is gone — see [Lease].
     */
    private val lease: Duration = 5.minutes,
    /** Written into `lockedBy`, and into every record's `appliedBy` by the runner. */
    private val owner: String = migrationIdentity(),
) : MigrationLedger {
    private val records: MongoCollection<Document> = database.getCollection(collection)
    private val locks: MongoCollection<Document> = database.getCollection("${collection}_lock")

    private val guard = Lease(lease, ::take, ::renew, ::release)

    /**
     * Inserts the lock document, and does nothing when it is already there.
     *
     * Seeded here rather than upserted inside [take], so that "declined" and "the lock row is
     * missing" stay two different things. Nothing creates the record collection: Mongo makes one on
     * first write, and the `_id` gives it the only uniqueness this ledger needs.
     */
    override suspend fun prepare() {
        val document = Document("_id", LOCK).append("lockedBy", null).append("lockedUntil", null)
        try {
            locks.insertOne(document)
        } catch (e: MongoWriteException) {
            if (e.error.category != ErrorCategory.DUPLICATE_KEY) throw e
        }
    }

    override suspend fun find(version: Long): MigrationRecord? = records.find(Filters.eq("_id", version)).firstOrNull()?.toRecord()

    /**
     * One `insertOne`, and the server's duplicate-key error is the refusal.
     *
     * The exception has to be the driver's own [MongoWriteException] rather than anything in
     * `stx-mongo`: `MongoDataException` has only a not-found and a pagination case, and a write
     * conflict is neither. The same idiom as `MongoWorkflowStore.create`.
     */
    override suspend fun claim(record: MigrationRecord): Boolean =
        try {
            records.insertOne(record.toDocument())
            true
        } catch (e: MongoWriteException) {
            if (e.error.category != ErrorCategory.DUPLICATE_KEY) throw e
            false
        }

    override suspend fun update(record: MigrationRecord) {
        records.replaceOne(Filters.eq("_id", record.version), record.toDocument())
    }

    override suspend fun blocking(staleAfter: Duration): MigrationRecord? {
        val cutoff = Date((Clock.System.now() - staleAfter).toEpochMilliseconds())
        return records
            .find(
                Filters.or(
                    Filters.eq("status", MigrationStatus.FAILED.name),
                    Filters.and(
                        Filters.eq("status", MigrationStatus.RUNNING.name),
                        Filters.lt("updatedAt", cutoff),
                    ),
                ),
            ).sort(Sorts.ascending("_id"))
            .firstOrNull()
            ?.toRecord()
    }

    override suspend fun all(): List<MigrationRecord> =
        records
            .find()
            .sort(Sorts.ascending("_id"))
            .map { it.toRecord() }
            .toList()

    /**
     * One conditional `updateOne` on one document, three times over — take, renew, release.
     *
     * Exactly the shape `MongoWorkflowStore` uses for its per-instance lock, with `_id` fixed: there
     * is one ledger and one lock over it. The guarantee is single-document atomicity and nothing
     * else, which is why this needs no replica set and no transaction.
     */
    override suspend fun <T> guarded(block: suspend () -> T): T? = guard.guard(LOCK, block)

    private suspend fun take(id: String): Boolean {
        val now = Clock.System.now()
        return locks
            .updateOne(
                Filters.and(
                    Filters.eq("_id", id),
                    Filters.or(Filters.eq("lockedUntil", null), Filters.lt("lockedUntil", Date(now.toEpochMilliseconds()))),
                ),
                Updates.combine(
                    Updates.set("lockedBy", owner),
                    Updates.set("lockedUntil", Date((now + lease).toEpochMilliseconds())),
                ),
            ).matchedCount == 1L
    }

    private suspend fun renew(id: String): Boolean =
        locks
            .updateOne(
                mine(id),
                Updates.set("lockedUntil", Date((Clock.System.now() + lease).toEpochMilliseconds())),
            ).matchedCount == 1L

    private suspend fun release(id: String) {
        locks.updateOne(mine(id), Updates.combine(Updates.set("lockedBy", null), Updates.set("lockedUntil", null)))
    }

    /** The lock, and only while this process still holds it — a lease that expired is somebody else's. */
    private fun mine(id: String) = Filters.and(Filters.eq("_id", id), Filters.eq("lockedBy", owner))

    private fun MigrationRecord.toDocument(): Document =
        Document("_id", version)
            .append("description", description)
            .append("status", status.name)
            .append("startedAt", Date(at.toEpochMilliseconds()))
            .append("updatedAt", Date(updatedAt.toEpochMilliseconds()))
            .append("appliedBy", appliedBy)
            .append("failure", failure)
            .append("durationMillis", durationMillis)

    private fun Document.toRecord(): MigrationRecord =
        MigrationRecord(
            version = getLong("_id"),
            description = getString("description"),
            status = MigrationStatus.valueOf(getString("status")),
            at = getDate("startedAt").asInstant(),
            updatedAt = getDate("updatedAt").asInstant(),
            appliedBy = getString("appliedBy"),
            failure = getString("failure"),
            durationMillis = get("durationMillis") as Long?,
        )

    private fun Date.asInstant(): Instant = Instant.fromEpochMilliseconds(time)
}
