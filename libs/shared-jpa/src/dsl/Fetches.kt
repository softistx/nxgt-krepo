package com.strange.jpa.dsl

import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import kotlin.reflect.KProperty1

/**
 * Loading an association in the same query that reads the owner, rather than in a query per row.
 *
 * ```kotlin
 * session
 *     .select<Purchase> {
 *         fetch(Purchase::customer)          // one select, not one per purchase
 *         fetchEach(Purchase::lines)
 *     }.list()
 * ```
 *
 * **This is the N+1 fix, and the numbers are measured rather than assumed.** Three rows whose
 * `@ManyToOne` points at three different owners cost **four** selects without a fetch join and
 * **one** with it — `FetchJoinTest` runs exactly that. The association is initialised before the
 * query returns, so reading it after the session closes works; without a fetch join the same read
 * is a `LazyInitializationException` for a lazy association, and a second select for an eager one.
 * The exception is the honest outcome of the two, because it fails where the mistake is; the eager
 * default fails silently as a query count nobody sees until production.
 *
 * **The join type defaults to `LEFT`, unlike [Joins.join], which defaults to `INNER`.** A join is a
 * filter and an inner one is what a caller usually means; a fetch is about *loading*, and an inner
 * fetch would silently drop every owner with no children — a filter nobody asked for. Passing
 * `JoinType.INNER` is still how a caller says they meant it.
 *
 * **A fetched association is an ordinary join as well**, because Hibernate answers `fetch` with a
 * node that is both, so what comes back is a [JoinScope] that indexes, filters and orders like any
 * other. One idiom, and the query does not join twice to do two things. Filtering *through* a
 * fetched collection is the one thing to be careful of: the rows the filter keeps are the whole
 * collection as far as the persistence context is concerned, so `lines` reads back incomplete and
 * says nothing about it. Filter through a separate [Joins.joinEach] when both are wanted.
 *
 * **`distinct` is not needed after a `fetchEach`.** Hibernate de-duplicates the owners of a fetched
 * collection itself — two purchases with four lines between them come back as two rows, not four.
 * That is the opposite of a plain [Joins.joinEach], which returns the owner once per element and is
 * what [QueryScope.distinct] exists for. `FetchJoinTest` pins both halves.
 *
 * **One level, deliberately.** There is no fetch from a fetch: nesting needs the parent to be
 * fetched too, and the type that could say so is a second `JoinScope` for a surface that would then
 * be twice the size. A query that has to fetch two levels is written against Criteria and run
 * through the same terminals — see *Criteria, where the DSL does not go*.
 *
 * Only [SelectScope] has these. A projection reads the columns it names and has no owner in its
 * select list to hang a fetch on, so a fetch there is a `SemanticException` from Hibernate at
 * execution — and a method that is always a runtime failure is better not offered, which is the
 * same rule that keeps [Joins] off the update and delete scopes.
 */
@JpaDsl
sealed interface Fetches<T : Any> : Joins<T> {
    /**
     * Fetches a to-one association, and answers with something to index.
     *
     * The same shape as [Joins.join] — the property may be nullable, and the nullability is dropped
     * from what comes back, because the join is on the entity either way.
     */
    fun <V : Any> fetch(
        property: KProperty1<T, V?>,
        type: JoinType = JoinType.LEFT,
    ): JoinScope<T, V> = fetched(property.name, type, collection = false)

    /**
     * Fetches a to-many association, whole.
     *
     * The collection is loaded with the owner, so a caller may read it after the session — which is
     * the point. It is also why `limit`, `offset` and `page` are refused on a query that does this:
     * the database applies them to the joined rows, so the last owner comes back holding part of its
     * collection and nothing says so. [QueryScope.limit] carries the measurement.
     */
    fun <E : Any> fetchEach(
        property: KProperty1<T, Collection<E>>,
        type: JoinType = JoinType.LEFT,
    ): JoinScope<T, E> = fetched(property.name, type, collection = true)

    @Suppress("UNCHECKED_CAST")
    private fun <V : Any> fetched(
        name: String,
        type: JoinType,
        collection: Boolean,
    ): JoinScope<T, V> {
        val existing = joins.taken[name]
        if (existing != null) {
            // An IllegalStateException rather than a JpaException, for the reason the join conflict
            // beside it is one: this is a programming error, not something about the data. Hibernate
            // has `isFetched` and `clearFetched` and no way to set it, so a join already taken cannot
            // be turned into a fetch — and asking for both emits two joins to the same table.
            check(existing.fetched) {
                "'$name' is already joined and a join cannot be turned into a fetch: " +
                    "fetch it first, and the join after it gives back the same one"
            }
            check(existing.type == type) {
                "'$name' is already fetched as ${existing.type} and this asks for $type: " +
                    "an association is fetched once, and asking again gives back the one already fetched"
            }
            return existing as JoinScope<T, V>
        }

        // Hibernate answers `fetch` with an SqmAttributeJoin, which implements both `JpaFetch` and
        // `JpaJoin` — so the cast is to an interface the object already has, and a fetched
        // association is filterable and indexable like any other. `FetchJoinTest` pins that.
        val join = from.fetch<T, V>(name, type) as Join<T, V>
        if (collection) joins.collectionFetched = true
        return JoinScope(join, type, fetched = true).also { joins.taken[name] = it }
    }
}
