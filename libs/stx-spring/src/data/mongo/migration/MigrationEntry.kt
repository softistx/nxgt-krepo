package com.strange.spring.data.mongo.migration

import org.bson.types.ObjectId
import org.springframework.data.annotation.TypeAlias
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.MongoId
import kotlin.time.Clock
import kotlin.time.Instant

/** Where one migration got to. */
enum class MigrationStatus {
    /** Recorded but not run yet — either it is about to be, or something before it failed. */
    PENDING,

    /** Ran without throwing. It will not run again. */
    APPLIED,

    /** Threw. Nothing after it runs until somebody deals with it — see [MigrationRunner]. */
    FAILED,
}

/**
 * The record of one migration, which is what makes "run once" mean anything.
 *
 * [code] is `<prefix><order>` — `V3` — and it is uniquely indexed, so two instances starting at the
 * same moment cannot both decide a migration has never run.
 *
 * As with `AuditEntry`, the collection is named by [MigrationStore] rather than by a SpEL `@Document`
 * expression: that expression needs a bean resolver and so resolves only inside an application
 * context. [COLLECTION] is the default.
 */
@TypeAlias(MigrationEntry.NAME)
@Document(MigrationEntry.COLLECTION)
data class MigrationEntry(
    @MongoId val id: String = ObjectId().toHexString(),
    /** `<prefix><order>`, and the identity of a migration. Uniquely indexed. */
    @Indexed(unique = true) val code: String,
    /** The number in the class name. Migrations run in this order. */
    val order: Int,
    /** `@MigrationUnit`'s description, or the name out of the class if it gave none. */
    val description: String = "",
    val status: MigrationStatus = MigrationStatus.PENDING,
    /** Why it failed, when it did. Null otherwise. */
    val failure: String? = null,
    val at: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
) {
    companion object {
        /** Where entries go unless `stx.data.mongo.migration.collection` says otherwise. */
        const val COLLECTION = "migrations"
        const val NAME = "MigrationEntry"
    }
}
