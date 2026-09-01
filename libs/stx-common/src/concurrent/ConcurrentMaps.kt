package com.strange.common.concurrent

import java.util.concurrent.ConcurrentMap

/**
 * The atomic `getOrPut`, under a name nobody confuses with the one that is not.
 *
 * ```kotlin
 * counters.getOrCompute(partition) { Counter(it) }
 * ```
 *
 * `kotlin.collections.getOrPut` compiles against a `ConcurrentMap` and is a `get`, a compute and a
 * `put` with nothing holding them together — see [Memo], which is the same argument at length. Use
 * this when the loader differs from one call site to the next; use [Memo] when one loader serves the
 * whole map, which is most of the time.
 *
 * The loader must not touch the same map: `ConcurrentHashMap` runs it while holding a bin lock.
 */
fun <K : Any, V : Any> ConcurrentMap<K, V>.getOrCompute(
    key: K,
    compute: (K) -> V,
): V = computeIfAbsent(key, compute)

/**
 * Reads and writes [key] as one step, in Kotlin's types.
 *
 * ```kotlin
 * offsets.update(partition) { last -> maxOf(last ?: 0, offset) }
 * highWater.update(partition) { null }        // returning null removes the entry
 * ```
 *
 * `ConcurrentMap.compute` already does this and is awkward from Kotlin twice over: it takes a
 * `BiFunction` whose arguments arrive as platform types, so the absent case is a `V!` that looks
 * non-null, and **returning null removes the entry** — a rule nothing in the signature says. This
 * spells both: the block is handed a real `V?`, and the removal is documented where it is written.
 *
 * The block may run more than once if another writer wins the race, so it must be a pure function of
 * what it is given — the same requirement `AtomicReference.updateAndGet` has, for the same reason.
 */
fun <K : Any, V : Any> ConcurrentMap<K, V>.update(
    key: K,
    block: (V?) -> V?,
): V? = compute(key) { _, current -> block(current) }
