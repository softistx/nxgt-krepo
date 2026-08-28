package com.strange.jpa.page

import com.strange.common.page.Page
import com.strange.common.page.PageInfo
import com.strange.jpa.JpaPaginationException
import com.strange.jpa.dsl.builder
import jakarta.persistence.criteria.Root
import kotlinx.coroutines.future.await
import org.hibernate.reactive.stage.Stage

/**
 * One page of a query, cut by keyset rather than by `offset`.
 *
 * ```kotlin
 * jpa.session { session ->
 *     session.selectPage<Purchase>(PageRequest.first(20)) {
 *         where { this[Purchase::total] gt 100L }
 *         sortBy(Purchase::total, descending = true)
 *         sortBy(Purchase::id)
 *     }
 * }
 * ```
 *
 * `offset(n)` makes the database walk and discard n rows, so the cost of a page grows with how deep
 * it is and page 500 is a scan. Resuming from the previous page's sort key costs the same at any
 * depth, and — the part that shows up in production rather than in a benchmark — it does not skip or
 * repeat a row when one is inserted between two requests.
 *
 * **The last sort key has to be the entity's identifier.** Keyset pagination resumes from a key, so
 * the key has to be unique: sort by a repeated column alone and every row sharing a value is a coin
 * toss between being served twice and being skipped. The identifier is the one column this library
 * can prove unique, so it is the one it insists on, and a sort without it is a
 * [JpaPaginationException] naming what is missing.
 *
 * One extra row is fetched beyond [PageRequest.limit]; whether it turned up is the whole answer to
 * *is there another page*, and it costs one row rather than a second query.
 *
 * Hibernate Reactive has no keyset pagination of its own — core's `getKeyedResultList` never reached
 * the reactive `SelectionQuery` — so the predicate, the cursors and the flip for a backward page are
 * this module's.
 */
suspend inline fun <reified T : Any> Stage.QueryProducer.selectPage(
    request: PageRequest,
    block: PageScope<T>.() -> Unit,
): Page<T> {
    val criteria = builder.createQuery(T::class.java)
    val scope = PageScope(criteria, criteria.from(T::class.java))
    scope.block()
    return pageOf(this, request, scope, T::class.java.simpleName)
}

/**
 * The part that is not worth inlining, which is all of it but the two reified lines.
 */
@PublishedApi
internal suspend fun <T : Any> pageOf(
    producer: Stage.QueryProducer,
    request: PageRequest,
    scope: PageScope<T>,
    entity: String,
): Page<T> {
    val keys = scope.keys
    val root = scope.from
    checkSort(keys, root, entity, scope.ordering.isEmpty())

    request.cursor?.let { cursor ->
        val values = decodeCursor(cursor, keys, root)
        scope.where { keysetPredicate(builder, root, keys, values, request.forward) }
    }
    scope.ordering += ordersOf(scope.builder, root, keys, request.forward)

    val query = producer.createQuery(scope.build())
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

private fun <T : Any> checkSort(
    keys: List<SortKey<T>>,
    root: Root<T>,
    entity: String,
    orderByUnused: Boolean,
) {
    if (!orderByUnused) {
        throw JpaPaginationException(
            "a paged query orders with sortBy, not orderBy: a cursor cannot be read back out of an expression",
        )
    }
    if (keys.isEmpty()) {
        throw JpaPaginationException("a paged query needs a sort: sortBy the identifier of $entity at least")
    }

    val identifier =
        runCatching { root.model.getId(root.model.idType.javaType).name }
            .getOrElse { throw JpaPaginationException("$entity has no single identifier to break ties on") }

    if (keys.last().name != identifier) {
        throw JpaPaginationException(
            "the sort on $entity ends with '${keys.last().name}', which is not unique: " +
                "add sortBy($entity::$identifier) last, or rows will be skipped and repeated",
        )
    }
}
