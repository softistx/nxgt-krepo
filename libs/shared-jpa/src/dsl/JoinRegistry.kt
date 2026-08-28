package com.strange.jpa.dsl

/**
 * The joins a query scope has taken so far, by attribute name.
 *
 * The DSL's own bookkeeping, and the reason asking for the same association twice gives back one
 * join rather than two. It exists as a class rather than as a `MutableMap` on [Joins] because Kotlin
 * allows neither `internal` nor `protected` on an interface member: the map had to be public there,
 * and a caller who emptied it got the duplicate join the memoization exists to prevent. A class can
 * make its member `internal`, so the type stays visible — a scope has to declare one — and nothing
 * on it does.
 */
class JoinRegistry<T : Any> {
    internal val taken: MutableMap<String, JoinScope<T, *>> = mutableMapOf()

    /**
     * Whether a [Fetches.fetchEach] has been taken, which is what makes `limit`, `offset` and `page`
     * unsafe on this query — see [QueryScope.limit].
     */
    internal var collectionFetched: Boolean = false
}
