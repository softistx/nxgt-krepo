package com.softistx.workflow.mongo

import com.softistx.mongo.codec.InstantAsBsonDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One workflow instance, as a document.
 *
 * [record] is the encoded [com.softistx.workflow.store.WorkflowRecord] and **a string, not a nested
 * document**. A store does not know the workflow's context type — the context is already a
 * `JsonElement` by the time it gets here, and no BSON codec maps one — so the choice is between a
 * string the store never looks inside and a mapping that would have to. Every other field exists
 * because a *query* needs it: [dueAt] to find what is due, the lease pair to hold an instance,
 * [expiresAt] for the TTL index. Nothing else is copied out, because two copies of a fact are one
 * chance for them to disagree.
 */
@Serializable
internal data class WorkflowInstanceDocument(
    @SerialName("_id") val id: String,
    val workflow: String,
    val record: String,
    /**
     * The instance's status, as its enum name.
     *
     * Duplicated out of [record] — the one thing that is — because a query cannot look inside a
     * string, and `find` exists to answer "which instances need a person".
     */
    val status: String,
    /** When the instance was last written. What `find` orders an operator's page by. */
    @Serializable(with = InstantAsBsonDateTime::class) val updatedAt: Instant,
    val version: Long,
    /** When this instance is next due. **Null means nothing polls for it** — parked, or finished. */
    @Serializable(with = InstantAsBsonDateTime::class) val dueAt: Instant? = null,
    val lockedBy: String? = null,
    @Serializable(with = InstantAsBsonDateTime::class) val lockedUntil: Instant? = null,
    /** When the TTL index deletes this instance. Null on anything still running, and on `Failed`. */
    @Serializable(with = InstantAsBsonDateTime::class) val expiresAt: Instant? = null,
)
