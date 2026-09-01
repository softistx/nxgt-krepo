package com.softistx.common.concurrent

import java.util.concurrent.ConcurrentHashMap

/**
 * A derived value computed once per key, for callers that cannot suspend.
 *
 * ```kotlin
 * private val serializers = Memo<Type, KSerializer<Any>> { resolveReflectively(it) }
 *
 * fun serializerFor(type: Type): KSerializer<Any> = serializers[type]
 * ```
 *
 * ## The mistake this exists to remove
 *
 * `ConcurrentHashMap` looks like it makes a cache safe on its own, and the obvious Kotlin spelling
 * quietly undoes it:
 *
 * ```kotlin
 * cache.getOrPut(key) { expensive(key) }      // NOT atomic, even on a ConcurrentHashMap
 * cache.computeIfAbsent(key) { expensive(it) } // atomic
 * ```
 *
 * `kotlin.collections.getOrPut` is a `get`, then a compute, then a `put` — three separate calls on
 * the map with nothing holding them together. Two threads arriving at a cold key **both** run the
 * loader. That is only a wasted computation when the value is a plain result, and a bug when the
 * value has identity: two loggers under one name, two parsed patterns, two connections, with one of
 * each silently dropped and whichever caller got the loser holding an object nobody else will ever
 * see again. It also fails at exactly the moment a cache is meant to help, which is the cold start
 * where everything asks at once.
 *
 * `MemoTest` pins the difference rather than asserting it: two threads enter the stdlib's `getOrPut`
 * at the same time, and cannot both enter this.
 *
 * ## What it asks of the loader
 *
 * **It must not touch this memo.** `ConcurrentHashMap` holds a bin lock while the loader runs, and a
 * loader that reaches back in deadlocks or throws — the JDK detects some of it and not all of it. A
 * value that needs another memoized value belongs in a second [Memo] that this one reads *before*
 * calling `get`.
 *
 * A loader that **throws** propagates and stores nothing, so the next caller tries again. That is
 * usually what a failed resolve wants: the alternative is caching the failure for the life of the
 * process.
 *
 * ## What it must not be used for
 *
 * It is **unbounded**, and there is no eviction on purpose — a memo of derived values wants a key
 * domain the program itself controls: a class, a `Type`, a logger name, a locale. Keys that come
 * from **outside** — a path, a tenant, an id in a request — turn this into a leak that a caller can
 * grow at will, and that is the shape of a denial of service rather than a cache.
 *
 * ## Which side of the boundary
 *
 * This one is for a caller that **cannot suspend**: a Hibernate binder, an SLF4J static initialiser,
 * a driver's callback. When every caller is a coroutine, the types in
 * `com.softistx.common.coroutines` are the ones that fit — `CoroutineSafeMap` when each operation
 * stands alone, `KeyedMutex` when the loader itself suspends.
 */
class Memo<K : Any, V : Any>(
    private val compute: (K) -> V,
) {
    private val values = ConcurrentHashMap<K, V>()

    /** The value for [key], computing it first if this is the first ask. */
    operator fun get(key: K): V = values.computeIfAbsent(key, compute)

    /** What is already there, without computing anything. */
    fun peek(key: K): V? = values[key]

    /**
     * Whether [key] has been computed.
     *
     * `containsKey` spelled out, because `key in map` on a `ConcurrentHashMap` does not mean what it
     * means on every other map: the class inherits `Hashtable`'s `contains`, which searches
     * **values**. Kotlin refuses to compile the `in` form for that reason (KT-18053) — a third
     * `ConcurrentHashMap` trap this type keeps out of its callers.
     */
    operator fun contains(key: K): Boolean = values.containsKey(key)

    /** Forgets [key], so the next [get] computes again. Returns what was dropped. */
    fun invalidate(key: K): V? = values.remove(key)

    fun clear() {
        values.clear()
    }

    val size: Int get() = values.size

    /** A copy, safe to iterate — the map itself is never handed out. */
    fun snapshot(): Map<K, V> = values.toMap()
}
