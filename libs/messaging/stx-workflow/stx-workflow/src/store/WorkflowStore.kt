package com.softistx.workflow.store

import com.softistx.workflow.WorkflowStatus
import kotlin.time.Instant

/**
 * Where instances live between two steps.
 *
 * Six methods, and deliberately not seven. This is the whole of what the engine asks of a store, so
 * it is the whole of what a new backing store has to answer for — a Mongo or a Postgres
 * implementation is a document or a row per instance, a conditional write on [WorkflowRecord.version],
 * an index on the due time and one on the status. Nothing here mentions Redis, and the core module
 * does not depend on it: the implementations live in `stx-workflow-db`, a module of its own, so that
 * the day a fourth store arrives nothing here moves.
 *
 * **Every implementation must make [save] conditional.** Returning true when the stored version was
 * not [expectedVersion] turns a lost race into a lost journal, which is the one failure this design
 * cannot recover from.
 */
interface WorkflowStore {
    /** Writes a record that must not already exist. */
    suspend fun create(record: WorkflowRecord)

    suspend fun load(id: String): WorkflowRecord?

    /**
     * Writes [record] if the stored version is still [expectedVersion], stamping it with
     * `expectedVersion + 1`. False means somebody else wrote first and this caller's copy is stale.
     */
    suspend fun save(
        record: WorkflowRecord,
        expectedVersion: Long,
    ): Boolean

    /**
     * The ids of instances that are due to be advanced, at most [limit] of them.
     *
     * "Due" is: not terminal, and either running with nobody advancing it — which is what a crashed
     * process leaves behind — or past its `wakeAt`: a sleep that is over, a start booked for a
     * moment that has come, a parent looking at its child again. Whoever asks is expected to try the
     * lock and move on quietly when somebody else has it.
     */
    suspend fun runnable(
        now: Instant,
        limit: Int,
    ): List<String>

    /**
     * The instances in [status], most recently updated first, at most [limit] of them.
     *
     * **This is what makes [com.softistx.workflow.WorkflowStatus.Failed] mean something.** That status
     * says a compensation could not be made to work and a person has to look — and until this method
     * existed there was no way to find one, because every other read here needs an id the operator
     * does not have. An engine that stops and makes a problem visible has to have somewhere the
     * problem is visible.
     *
     * There is no filter by workflow name. A store would have to index a second dimension for it,
     * differently in each implementation, to save a caller a `filter` over a page it already has.
     *
     * [offset] rather than a cursor, because the three stores order by the same thing and page it
     * three different ways, and an offset means the same thing in all of them. This is an operator's
     * inbox, not a feed: deep paging is not the shape of the problem.
     */
    suspend fun find(
        status: WorkflowStatus,
        limit: Int = 50,
        offset: Int = 0,
    ): List<WorkflowRecord>

    /**
     * Runs [block] while holding [id]'s lock, or returns null without running it when somebody else
     * holds it.
     *
     * The lock is held across suspending work, on purpose: it says "this instance is being advanced
     * right now", and a step that takes a minute is still one instance being advanced. An
     * implementation over a lock with a TTL therefore has to renew it while [block] runs.
     */
    suspend fun <T> guarded(
        id: String,
        block: suspend () -> T,
    ): T?
}
