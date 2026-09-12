package com.softistx.workflow.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.Length
import kotlin.time.Instant

/**
 * One workflow instance, as a row.
 *
 * The application has to include this class in its `Jpa.connect(...)` entities or in the packages
 * `Jpa.scan` reads, and after that it is Hibernate's problem which database this is — the driver
 * comes from the URI's scheme, exactly as it does for every other entity in the application. That is
 * the whole reason Postgres and DB2 need no code between them here.
 *
 * **`record` is the encoded [com.softistx.workflow.store.WorkflowRecord], and nothing here reads it.**
 * Every other column exists because a *query* needs it — `due_at` to find what is due, the lease
 * pair to hold an instance, `finished_at` to purge. Nothing else is copied out of the document:
 * two copies of a fact are one chance for them to disagree.
 */
@Entity
@Table(
    name = "stx_workflow_instance",
    indexes = [
        Index(name = "ix_stx_workflow_due", columnList = "due_at"),
        Index(name = "ix_stx_workflow_status", columnList = "status, updated_at"),
    ],
)
class WorkflowInstanceRow(
    @Id
    @Column(length = 200)
    var id: String = "",
    @Column(nullable = false, length = 200)
    var workflow: String = "",
    /**
     * The instance's status, as its enum name.
     *
     * Duplicated out of [record] — the one thing that is — because a query cannot look inside a
     * string. `find` exists to answer "which instances need a person", and the alternative to a
     * column is reading every row and decoding it.
     */
    @Column(nullable = false, length = 20)
    var status: String = "",
    /** When the instance was last written. What `find` orders an operator's page by. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.DISTANT_PAST,
    /**
     * `Length.LONG32` rather than `@Lob`, and the difference is not cosmetic.
     *
     * Hibernate maps this to the dialect's largest character type — `text` on Postgres, `clob` on
     * DB2 — which is a column any other client can read. `@Lob` on a `String` has historically
     * meant a Postgres large-object `oid`, a handle into a side table that `select` shows as a
     * number and `pg_dump` treats specially. A workflow journal an operator cannot read in psql is
     * a journal that is not there when it is needed.
     */
    @Column(nullable = false, length = Length.LONG32)
    var record: String = "",
    /** Bumped and checked by every write. Not `@Version`: see [JpaWorkflowStore.save]. */
    @Column(nullable = false)
    var version: Long = 0,
    /** When this instance is next due. **Null means nothing polls for it** — parked, or finished. */
    var dueAt: Instant? = null,
    @Column(length = 100)
    var lockedBy: String? = null,
    var lockedUntil: Instant? = null,
    /** Set when the instance reached a terminal status, and read only by [JpaWorkflowStore.purge]. */
    var finishedAt: Instant? = null,
)
