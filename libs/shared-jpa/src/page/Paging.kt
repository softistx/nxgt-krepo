package com.strange.jpa.page

import com.strange.common.page.Page
import com.strange.common.page.pageOf
import com.strange.jpa.JpaPaginationException
import com.strange.jpa.dsl.SelectScope
import com.strange.jpa.query.JpaQuery
import com.strange.jpa.query.hql

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

    // Through JpaQuery rather than the raw Stage query, so a paged query that fails names itself in
    // HQL like every other one, and `readOnly` is applied rather than quietly lost.
    val query = JpaQuery<T>({ criteria.hql() }, producer.createQuery(criteria))
    if (resultsReadOnly) query.readOnly()
    val limit = request.limit
    if (limit != null) query.limit(limit + 1)

    return pageOf(
        rows = query.list(),
        limit = limit,
        forward = request.forward,
        resumed = request.cursor != null,
        cursorOf = { encodeCursor(it, keys) },
        valueOf = { it },
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

    // A page is a limit by another name, and a fetched collection makes a limit silently wrong —
    // the database applies it to the joined rows, so the last owner of the page comes back holding
    // part of its collection. `QueryScope.limit` carries the measurement.
    if (joins.collectionFetched) {
        throw JpaPaginationException(
            "a paged query cannot fetchEach: the page would be cut across the joined rows, so its " +
                "last row would hold part of its collection and say nothing. Page the owners here " +
                "and fetch their collections in a second query off the ids, or project the columns " +
                "the page shows",
        )
    }

    // A page takes its size from the request, so these would be silently overruled by it. Refused
    // rather than ignored, the way `orderBy` above is: a call that means nothing should say so.
    if (rowLimit != null || rowOffset != null) {
        throw JpaPaginationException(
            "a paged query sizes itself from PageRequest, not limit/offset: " +
                "pass the size to first(n) or last(n) and drop the builder call",
        )
    }

    // Both of these are about failing on the page nobody follows rather than the page after it.
    // A cursor is written by `toString()` and read back through a table, so a key the table cannot
    // parse — or an attribute the mapping does not have — issues page one and a cursor that looks
    // fine, and breaks only when somebody uses it.
    keys.forEach { key ->
        val attribute =
            runCatching { from.model.getSingularAttribute(key.name) }
                .getOrElse {
                    throw JpaPaginationException(
                        "$entity has no mapped attribute '${key.name}' to page by: " +
                            "a sort key has to be a persistent column, not a computed or @Transient one",
                    )
                }
        if (!carriesCursorValue(attribute.javaType)) {
            throw JpaPaginationException(
                "a ${attribute.javaType.name} cannot be a cursor key, so '${key.name}' " +
                    "cannot be sorted on in a paged query",
            )
        }
    }
}
