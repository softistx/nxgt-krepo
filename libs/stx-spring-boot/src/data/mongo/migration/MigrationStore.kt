package com.softistx.spring.data.mongo.migration

import com.softistx.spring.data.mongo.criteria.eq
import com.softistx.spring.data.mongo.criteria.query
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.exists
import org.springframework.data.mongodb.core.findOne
import org.springframework.data.mongodb.core.index.Index

/**
 * Where migration records are read and written.
 *
 * A class over `ReactiveMongoTemplate` for the same reason as `AuditStore`: a repository interface
 * is only a bean when something scans for it, and shipping one would make an application point
 * `@EnableReactiveMongoRepositories` at this library's package to use the feature at all.
 *
 * [migrations] is passed to every call rather than written into [MigrationEntry]'s `@Document`,
 * because the SpEL form of a configurable collection name needs a bean resolver and fails outside an
 * application context.
 */
class MigrationStore(
    private val template: ReactiveMongoTemplate,
    private val migrations: String = MigrationEntry.COLLECTION,
) {
    /**
     * Creates the unique index on `code`.
     *
     * Called before a run rather than left to `IndexInitializer`, because that one only sees types
     * the mapping context has already met — and the whole point of this index is to be there the
     * first time a migration is recorded, on a database where nothing has been recorded yet.
     *
     * **`named("code")` is load-bearing.** `MigrationEntry.code` also carries `@Indexed`, so
     * `IndexInitializer` creates the same index whenever `stx.data.mongo.create-indexes` is on —
     * under the name Spring Data derives from the property, which is `code`. Left unnamed, this one
     * asks for `code_1`, Mongo answers `IndexOptionsConflict: Index already exists with a different
     * name`, and the whole migration run aborts into a log line nobody reads. Two features that are
     * each meant to be safe on every boot are not safe together unless they agree about the name.
     */
    suspend fun prepare() {
        template
            .indexOps(migrations)
            .createIndex(Index().on("code", Sort.Direction.ASC).unique().named("code"))
            .awaitFirstOrNull()
    }

    /** The record for one code, or null when this migration has never been seen. */
    suspend fun find(code: String): MigrationEntry? =
        template.findOne<MigrationEntry>(("code" eq code).query, migrations).awaitFirstOrNull()

    /** Whether any migration is recorded as failed. */
    suspend fun anyFailed(): Boolean =
        template
            .exists<MigrationEntry>(("status" eq MigrationStatus.FAILED.name).query, migrations)
            .awaitSingle()

    /**
     * Records a migration for the first time, or returns null when another instance got there first.
     *
     * An insert rather than a save, so the unique index on `code` is what decides. Two instances
     * starting together cannot both conclude a migration has never run.
     */
    suspend fun claim(entry: MigrationEntry): MigrationEntry? =
        try {
            template.insert(entry, migrations).awaitSingle()
        } catch (_: DuplicateKeyException) {
            null
        }

    /** Writes back a record whose status changed. */
    suspend fun update(entry: MigrationEntry): MigrationEntry = template.save(entry, migrations).awaitSingle()
}
