package com.strange.jpa.query

import com.strange.common.page.Page
import com.strange.common.page.PageInfo
import com.strange.jpa.criteria.JpaSpec
import com.strange.jpa.session.JpaQueries
import com.strange.jpa.session.createQuery
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root

/**
 * The criteria the reads below are built from, for everything they do not spell.
 *
 * **This is where a fetch and an ordering go.** [shape] is handed the criteria and its root, so
 * anything Criteria can say is available — a fetch join, an order, a second restriction, a
 * `distinct`. It answers with entities, so a caller that will read an association has to ask for it:
 * associations are `LAZY` and Hibernate Reactive has no transparent lazy loading, so an unfetched one
 * throws rather than costing a second select.
 *
 * ```kotlin
 * session.select<Purchase>({ it[Purchase::total] gt 100L }) { _, purchase ->
 *     purchase.fetch(Purchase::customer)
 * }.list()
 * ```
 *
 * A [JpaSpec] deliberately gets the root and nothing else, so a spec cannot fetch. That keeps one
 * named restriction usable from a [findAll], a [count] and a projection alike — a projection has no
 * owner in its select list to hang a fetch on, and Hibernate refuses one there.
 *
 * [shape] runs after [spec], so a `criteria.where` inside it replaces the restriction rather than
 * adding to it. Restrictions belong in the spec, where they compose with `and`; [shape] is for the
 * rest.
 */
inline fun <reified T : Any> JpaQueries.select(
    noinline spec: JpaSpec<T>? = null,
    shape: (CriteriaQuery<T>, Root<T>) -> Unit = { _, _ -> },
): JpaQuery<T> {
    val criteria = createQuery<T>()
    val root = criteria.from(T::class.java)

    criteria.select(root)
    spec?.invoke(root)?.let(criteria::where)
    shape(criteria, root)

    return query(criteria)
}

/**
 * Every row [spec] keeps, or the whole table when there is no spec.
 *
 * There is no `shape` here on purpose: a trailing lambda should mean the restriction, which is what
 * a caller writes nine times in ten. A read that needs a fetch or an ordering says so through
 * [select], which is the same query with the criteria still in hand — `select(spec) { … }.list()`.
 */
suspend inline fun <reified T : Any> JpaQueries.findAll(noinline spec: JpaSpec<T>? = null): List<T> = select(spec).list()

/** The first row [spec] keeps, or null. [findAll] explains the missing `shape`. */
suspend inline fun <reified T : Any> JpaQueries.findOne(noinline spec: JpaSpec<T>): T? = select(spec).first()

/** How many rows [spec] keeps. */
suspend inline fun <reified T : Any> JpaQueries.count(noinline spec: JpaSpec<T>? = null): Long = select(spec).count()

/**
 * Whether anything matches — one row asked for, not a count of all of them.
 *
 * `count(*) > 0` makes the database aggregate the entire match set to answer a boolean; a limit of
 * one lets it stop at the first row it finds.
 */
suspend inline fun <reified T : Any> JpaQueries.exists(noinline spec: JpaSpec<T>): Boolean = select(spec).limit(1).first() != null

/**
 * One page, cut by [limit] and [offset].
 *
 * ```kotlin
 * session.findPage<Product>(limit = 20, offset = 40, spec = available) { criteria, product ->
 *     criteria.orderBy(asc(product[Product::name]), asc(product[Product::id]))
 * }
 * ```
 *
 * It asks for one row beyond the page, and whether that row turned up is the whole answer to *is
 * there another page* — one row rather than a second `count` query. The cursors in [PageInfo] stay
 * null: they belong to keyset pagination, which this is not.
 *
 * **`offset` makes the database walk and discard**, so a page costs more the deeper it is, and a row
 * inserted between two requests shifts the window — page 3 can repeat or skip a row. That is fine
 * for a few hundred rows behind a UI and wrong for an export or an infinite scroll over a table that
 * is being written to. For those, order by the identifier and resume from the last one seen:
 * `where(product[Product::id] gt lastSeen)` with a `limit`, which costs the same at any depth. This
 * is the convenience, not the recommendation.
 *
 * **An ordering is not optional**, and nothing can enforce it here: without one the database may
 * answer in any order it likes, and two pages of an unordered query are not two pages of anything.
 * Say it in [shape].
 */
suspend inline fun <reified T : Any> JpaQueries.findPage(
    limit: Int,
    offset: Int = 0,
    noinline spec: JpaSpec<T>? = null,
    shape: (CriteriaQuery<T>, Root<T>) -> Unit = { _, _ -> },
): Page<T> {
    require(limit >= 1) { "a page size must be at least 1, got $limit" }
    require(offset >= 0) { "an offset cannot be negative, got $offset" }

    val rows =
        select(spec, shape)
            .offset(offset)
            .limit(limit + 1)
            .list()

    return Page(
        data = rows.take(limit),
        info = PageInfo(hasPreviousPage = offset > 0, hasNextPage = rows.size > limit),
    )
}
