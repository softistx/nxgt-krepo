package com.strange.workflow.redis

import com.strange.redis.Redis

/**
 * Where an instance lives, and where the ones waiting to be advanced are listed.
 *
 * Everything goes through [Redis.key], so a caller's `RedisConfig.namespace` prefixes all of it and
 * two applications — or an application and its own tests — can share a server without being able to
 * delete each other's instances.
 *
 * The instance key does **not** carry the workflow's name. `load` is given an id and nothing else,
 * so a key it cannot build from an id alone is a key it cannot read.
 */
internal fun Redis.instanceKey(id: String): String = key("wf", "instance", id)

/**
 * The sorted set of instances due to be advanced, scored by the time they are due.
 *
 * A sorted set rather than a stream, and the difference is what each one is *for*. A stream is a log
 * of things that happened, delivered once and acknowledged; an instance waiting to be advanced is
 * not an event, it is a **state**, and it stays true until somebody changes it. Scoring by due time
 * answers the worker's whole question in one round trip — `ZRANGEBYSCORE 0 now` — and re-scoring the
 * same id updates it in place instead of adding a second entry, which is exactly what a checkpoint
 * needs to do. A stream would have grown one entry per checkpoint and left a pending list to
 * reconcile against the record that already knows the answer.
 *
 * It is also the shape the phase after this one needs: a timer and a wake-up from an approval are
 * both "due at T", which is a score.
 */
internal fun Redis.runnableKey(): String = key("wf", "runnable")

/**
 * The prefix of the per-status indexes: one sorted set per [com.strange.workflow.WorkflowStatus],
 * scored by the time the instance was last written.
 *
 * One set per status rather than one set for everything, because the query this exists for is
 * "show me the ones that need a person" and `Failed` is meant to be rare. A single index would have
 * buried it under every instance that ever completed.
 *
 * Scored by `updatedAt` so a page comes back newest first, which is the order an inbox is read in.
 *
 * The scripts build these keys **inside Lua**, from this prefix, because a write has to remove the
 * id from its old status's set and the old status is not known until the hash is read. That is a key
 * the script does not declare, so this store — like `scanKeys` in `stx-redis` — assumes a single
 * logical Redis rather than a cluster.
 */
internal fun Redis.statusPrefix(): String = key("wf", "status", "")

/** The name [com.strange.redis.lock.RedisLock] builds its own key from. */
internal fun instanceLockName(id: String): String = "wf:$id"
