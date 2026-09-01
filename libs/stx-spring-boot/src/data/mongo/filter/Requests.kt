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
 */
fun List<SortOrder>.toSort(): Sort =
    if (isEmpty()) {
        Sort.unsorted()
    } else {
        Sort.by(map { if (it.descending) Sort.Order.desc(it.property) else Sort.Order.asc(it.property) })
    }

/** The `?filter=` parameter of this request, as a query. */
val ServerRequest.mongoFilter: Query get() = queryParamOrNull("filter").parseFilter()

/**
 * `?filter=` and `?sort=` together, which is what a list route actually wants.
 *
 * Paging is deliberately not applied: `limit`/`skip` on a query is offset paging, and which of that
 * or a keyset cursor a route wants is the route's decision, not a helper's.
 */
val ServerRequest.mongoQuery: Query get() = mongoFilter.with(sort.toSort())

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
 */
fun ServerRequest.mongoPage(
    size: Int = this.size,
    cursor: String? = this.cursor,
): MongoPage = MongoPage.first(size, cursor = cursor, query = mongoFilter, sort = sort.toSort())
