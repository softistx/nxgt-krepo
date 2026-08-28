package com.strange.jpa.audit

import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Who wrote this row, and when.
 *
 * ```kotlin
 * @Entity
 * class Note(@Id var id: Long = 0, var text: String = "") : AuditedEntity()
 * ```
 *
 * **The timestamps are Hibernate's, the names are the service's.** `@PrePersist` and `@PreUpdate`
 * run inside the flush, which means *when* is stamped exactly when a row is actually written — an
 * update that the dirty check decides is a no-op fires neither callback and moves no timestamp. A
 * service comparing fields could not tell the difference, which is why this half is not the
 * service's job. The other half is: only a service knows the principal, so `JpaCrudService` fills in
 * [createdBy] and [lastModifiedBy].
 *
 * The four names are `shared-mongo`'s `AuditMetadata`, so a caller reading an audit trail asks the
 * same question of either store. Mongo nests them under a `metadata` sub-document because a document
 * has somewhere to nest; a table does not, so here they are four columns — `created_at`,
 * `last_modified_at`, `created_by`, `last_modified_by` under the snake-case naming strategy.
 *
 * The timestamps default to the epoch rather than to `now`, because a value that looks plausible is
 * worse than one that is obviously unset: a row written through a stateless session runs no
 * callbacks, and an epoch stamp says so.
 */
@MappedSuperclass
abstract class AuditedEntity {
    var createdAt: Instant = Instant.fromEpochSeconds(0)

    var lastModifiedAt: Instant = Instant.fromEpochSeconds(0)

    var createdBy: String = ""

    var lastModifiedBy: String = ""

    @PrePersist
    protected fun stampCreation() {
        val now = Clock.System.now()
        createdAt = now
        lastModifiedAt = now
    }

    @PreUpdate
    protected fun stampModification() {
        lastModifiedAt = Clock.System.now()
    }
}
