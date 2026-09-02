package com.softistx.jpa.criteria

import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import kotlin.reflect.KProperty1

/**
 * Joins a to-one association *and loads it*, in one pass over the table.
 *
 * ```kotlin
 * val purchase = criteria.from(Purchase::class.java)
 * val buyer = purchase.fetch(Purchase::customer)
 * criteria.where(buyer[Buyer::name] eq "ada")
 * ```
 *
 * This is the answer to N+1. A `@ManyToOne` is EAGER by JPA's default, so a page of 50 purchases
 * with 50 distinct customers costs 50 extra selects after the one that fetched the purchases —
 * fetching the association folds all of it into a single join. `entityFetchCount` on the factory's
 * statistics is the number to watch: a spec asserts it goes to zero here.
 *
 * The result is a [Join], so it is also the thing later paths hang off — `buyer[Buyer::name]` above
 * restricts on the same join that loads it, rather than emitting a second one. That matters because
 * **a plain join cannot be upgraded to a fetch afterwards.** `SqmAttributeJoin` has `isFetched` and
 * `clearFetched` and no `setFetched`, so asking for a join and then a fetch of the same association
 * emits two joins. Fetch first; the value it returns is the join.
 *
 * The default is [JoinType.LEFT], which is the one that does not change *which* rows come back —
 * an inner fetch join drops owners whose association is null, and doing that by accident while
 * meaning only to preload is the classic fetch-join bug.
 *
 * **A fetch has no place in a projection.** `select title, customer` with a fetched join is a
 * `SemanticException` from Hibernate: a fetch says *fill this object in*, and a projection is not
 * returning the object to fill. Use a plain [join] there.
 */
fun <T : Any, V : Any> From<*, T>.fetch(
    property: KProperty1<T, V?>,
    type: JoinType = JoinType.LEFT,
): Join<T, V> = fetched(property.name, type)

/**
 * Joins a to-many association and loads its elements, once per element.
 *
 * ```kotlin
 * val lines = purchase.fetchEach(Purchase::lines)
 * ```
 *
 * **A collection fetch and a row limit do not compose, and nothing refuses the combination.** The
 * limit is applied to the *joined* rows, so a purchase with three lines under `limit(2)` comes back
 * as one purchase holding two of its three lines — silently incomplete, and cached that way for the
 * rest of the session. Measured, not inferred: it is the same truncation an entity graph naming a
 * collection produces. Fetch the collection on a query that returns whole owners, and pull the
 * collection separately for a page.
 *
 * Duplicate owners are not the hazard folklore says they are — Hibernate 7 de-duplicates entity
 * results whether the join fetches or not, so three purchases with five lines between them come back
 * as three. A *projection* over the same join returns five rows, which is usually what a projection
 * wanted.
 */
fun <T : Any, E : Any> From<*, T>.fetchEach(
    property: KProperty1<T, Collection<E>>,
    type: JoinType = JoinType.LEFT,
): Join<T, E> = fetched(property.name, type)

/**
 * The cast the two above share.
 *
 * `From.fetch` is declared to return a [jakarta.persistence.criteria.Fetch], which carries no path
 * and so cannot be restricted on — but Hibernate's implementation is `SqmAttributeJoin`, which is a
 * [Join]. Widening it is what lets a fetched association be filtered and selected from like any
 * other join, and there is no second implementation of the criteria tree in the runtime. A spec
 * asserts it rather than this comment.
 */
@Suppress("UNCHECKED_CAST")
private fun <T : Any, V : Any> From<*, T>.fetched(
    name: String,
    type: JoinType,
): Join<T, V> = fetch<T, V>(name, type) as Join<T, V>
