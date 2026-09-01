package com.strange.workflow.store

import kotlin.time.Instant

/**
 * Where instances live between two steps.
 *
 * Five methods, and deliberately not six. This is the whole of what the engine asks of a store, so
 * it is the whole of what a new backing store has to answer for — a Mongo or a Postgres
 * implementation is a document or a row per instance, a conditional write on [WorkflowRecord.version],
 * and an index on the due time. Nothing here mentions Redis, and the core module does not depend on
 * it: the implementations live in `stx-workflow-db`, a module of its own, so that the day a fourth
 * store arrives nothing here moves.
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
     * process leaves behind — or, once phase two adds timers, past its `wakeAt`. Whoever asks is
     * expected to try the lock and move on quietly when somebody else has it.
     */
    suspend fun runnable(
        now: Instant,
        limit: Int,
    ): List<String>

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
