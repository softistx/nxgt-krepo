package com.strange.jpa.page

import com.strange.common.page.Page
import com.strange.common.page.PageInfo
import com.strange.jpa.JpaPaginationException
import com.strange.jpa.dsl.SelectScope
import kotlinx.coroutines.future.await

/**
 * One page of a query, cut by keyset rather than by `offset`.
 *
 * ```kotlin
 * jpa.session { session ->
 *     session
 *         .select<Purchase>()
 *         .where { Purchase::total gt 100L }
 *         .sortBy(Purchase::total, descending = true)
 *         .sortBy(Purchase::id)
 *         .page(PageRequest.first(20))
 * }
 * ```
 *
 * A terminal like `list` or `count`, and reached the same way — there is no separate entry point for
 * a paged query, only a different way to end one.
 *
 * `offset(n)` makes the database walk and discard n rows, so the cost of a page grows with how deep
 * it is and page 500 is a scan. Resuming from the previous page's sort key costs the same at any
 * depth, and — the part that shows up in production rather than in a benchmark — it does not skip or
 * repeat a row when one is inserted between two requests.
 *
 * **`sortBy`, not `orderBy`.** A cursor is the sort key of the row it points at, so the page has to
 * read those values back off the row that came out; `orderBy` takes an expression and there is no
 * way back from one to a value. A query that used `orderBy` is refused rather than quietly paged
 * along a key its cursors do not carry.
 *
 * **The last sort key has to be the entity's identifier**, and a sort that does not end in it is a
 * [JpaPaginationException] naming what to add. Keyset pagination resumes from a key, so the key has
 * to be unique: sort by a repeated column alone and every row sharing a value is a coin toss between
 * being served twice and being skipped — a data bug that reads as a UI bug. The identifier is the
 * one column this library can prove unique, so it is the one it insists on.
 *
 * **The query is not consumed by being paged**, so the same one answers for every page: the keyset
 * predicate is handed to the build for this page rather than added to the query, and the next page
 * replaces it. That is what makes `first(20)` and then `first(20, endCursor)` off one query the
 * ordinary way to walk, rather than a way to intersect two cursors into an empty page.
 *
 * One extra row is fetched beyond [PageRequest.limit]; whether it turned up is the whole answer to
 * *is there another page*, and it costs one row rather than a second query.
 *
 * Hibernate Reactive has none of this — core's `getKeyedResultList` never reached the reactive
 * `SelectionQuery` — so the predicate, the cursors and the flip for a backward page are this
 * module's. There is no row-value comparison in the criteria builder either, so the keyset predicate
 * expands to the lexicographic `or`-chain a composite index satisfies with a seek.
 */
suspend fun <T : Any> SelectScope<T>.page(request: PageRequest): Page<T> {
    checkSort()

    val resume =
        request.cursor?.let { cursor ->
            val values = decodeCursor(cursor, keys, from)
            keysetPredicate(builder, from, keys, values, request.forward)
        }

    val criteria = build(resume)
    criteria.orderBy(ordersOf(builder, from, keys, request.forward))

    val query = producer.createQuery(criteria)
    val limit = request.limit
    if (limit != null) query.setMaxResults(limit + 1)

    val rows = query.resultList.await()
    val more = limit != null && rows.size > limit
    val page = (if (limit == null) rows else rows.take(limit)).let { if (request.forward) it else it.asReversed() }

    return Page(
        data = page,
        info =
            PageInfo(
                startCursor = page.firstOrNull()?.let { encodeCursor(it, keys) },
                endCursor = page.lastOrNull()?.let { encodeCursor(it, keys) },
                hasPreviousPage = if (request.forward) request.cursor != null else more,
                hasNextPage = if (request.forward) more else request.cursor != null,
            ),
    )
}

private fun <T : Any> SelectScope<T>.checkSort() {
    if (ordering.isNotEmpty()) {
        throw JpaPaginationException(
            "a paged query orders with sortBy, not orderBy: a cursor cannot be read back out of an expression",
        )
    }

    val entity = from.model.name
    if (keys.isEmpty()) {
        throw JpaPaginationException("a paged query needs a sort: sortBy the identifier of $entity at least")
    }

    val identifier =
        runCatching { from.model.getId(from.model.idType.javaType).name }
            .getOrElse { throw JpaPaginationException("$entity has no single identifier to break ties on") }

    if (keys.last().name != identifier) {
        throw JpaPaginationException(
            "the sort on $entity ends with '${keys.last().name}', which is not unique: " +
                "add sortBy($entity::$identifier) last, or rows will be skipped and repeated",
        )
    }
}
