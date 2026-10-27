package com.softistx.workflow.jpa

import com.softistx.jpa.Jpa
import com.softistx.jpa.session.transaction
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * The two `update`s a [com.softistx.common.coroutines.Lease] is made of, and the release that undoes
 * them, on the `locked_by` / `locked_until` pair of [WorkflowInstanceRow].
 */
internal class JpaLeaseQueries(
    private val jpa: Jpa,
    private val lease: Duration,
) {
    /** Who this process is, in `locked_by`. One per store, so a release can prove it holds the lease. */
    private val owner: String = UUID.randomUUID().toString()

    suspend fun take(id: String): Boolean {
        val now = Clock.System.now()
        return jpa.transaction { session ->
            session
                .mutate(
                    """
                    update WorkflowInstanceRow r set r.lockedBy = :owner, r.lockedUntil = :until
                     where r.id = :id and (r.lockedUntil is null or r.lockedUntil < :now)
                    """.trimIndent(),
                ).parameters("owner" to owner, "until" to now + lease, "id" to id, "now" to now)
                .execute()
        } == 1
    }

    suspend fun renew(id: String): Boolean =
        jpa.transaction { session ->
            session
                .mutate("update WorkflowInstanceRow r set r.lockedUntil = :until where r.id = :id and r.lockedBy = :owner")
                .parameters("until" to Clock.System.now() + lease, "id" to id, "owner" to owner)
                .execute()
        } == 1

    suspend fun release(id: String) {
        // The row may be gone — a purge, or a spec that dropped its schema — and a release that
        // failed is a lease that expires on its own a moment later. Nothing here is worth failing
        // a completed step for.
        runCatching {
            jpa.transaction { session ->
                session
                    .mutate(
                        "update WorkflowInstanceRow r set r.lockedBy = null, r.lockedUntil = null " +
                            "where r.id = :id and r.lockedBy = :owner",
                    ).parameters("id" to id, "owner" to owner)
                    .execute()
            }
        }.onFailure { if (it is CancellationException) throw it }
    }
}
