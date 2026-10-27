package com.softistx.spring.data.mongo.filter

import com.softistx.spring.data.mongo.template.MongoPage
import com.softistx.spring.web.SortOrder
import com.softistx.spring.web.cursor
import com.softistx.spring.web.size
import com.softistx.spring.web.sort
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.queryParamOrNull

/**
 * The bridge from what `web/` parsed to what Spring Data sorts by.
 *
 * It lives here and not in `web/` because `Sort` is a spring-data-commons type: a route reading a
 * query parameter should not put a data-access library on its consumer's classpath. This is the
 * package that already has one.
 *
 * With [allowed], a property outside that set fails as `filters.invalid`, the way an unlisted filter
 * field does; null sorts by whatever the client named. Dotted paths are matched whole, as in
 * [parseFilter].
 */
fun List<SortOrder>.toSort(allowed: Set<String>? = null): Sort =
    if (isEmpty()) {
        Sort.unsorted()
    } else {
        Sort.by(
            map {
                if (allowed != null && it.property !in allowed) throw invalid(it.clause)
                if (it.descending) Sort.Order.desc(it.property) else Sort.Order.asc(it.property)
            },
        )
    }

private val SortOrder.clause get() = "$property:${if (descending) "DESC" else "ASC"}"

/** The `?filter=` parameter of this request, as a query. Every field is filterable: see [mongoFilter]. */
val ServerRequest.mongoFilter: Query get() = mongoFilter(null)

/** [mongoFilter] restricted to [allowed] — a field outside it fails as `filters.invalid`; null allows all. */
fun ServerRequest.mongoFilter(allowed: Set<String>?): Query = queryParamOrNull("filter").parseFilter(allowed)

/**
 * `?filter=` and `?sort=` together, which is what a list route actually wants.
 *
 * Paging is deliberately not applied: `limit`/`skip` on a query is offset paging, and which of that
 * or a keyset cursor a route wants is the route's decision, not a helper's.
 */
val ServerRequest.mongoQuery: Query get() = mongoQuery(null)

/** [mongoQuery] restricted to [allowed] for both `?filter=` and `?sort=`; null allows every field. */
fun ServerRequest.mongoQuery(allowed: Set<String>?): Query = mongoFilter(allowed).with(sort.toSort(allowed))

/**
 * The whole request as a page window — `?filter=`, `?sort=`, `?size=` and `?cursor=` together.
 *
 * ```kotlin
 * val page = template.findPage<Order>(request.mongoPage())
 * ```
 *
 * **This rather than `MongoPage.first(size, query = mongoQuery)`.** That spelling puts the ordering
 * on the query, where the keyset machinery cannot see it: the rows come back sorted and the cursor is
 * built from `_id` alone, so the first page is right, the second is empty, and nothing says so.
 * [MongoPage] now refuses it outright — this is what to write instead.
 *
 * Pass [allowed] — the fields a client may filter and sort on — on any route over a collection with
 * a field that must not leak; see [parseFilter]. Null allows every field.
 */
fun ServerRequest.mongoPage(
    size: Int = this.size,
    cursor: String? = this.cursor,
    allowed: Set<String>? = null,
): MongoPage =
    MongoPage.first(
        size,
        cursor = cursor,
        query = mongoFilter(allowed),
        sort = sort.toSort(allowed),
    )
