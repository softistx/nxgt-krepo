package com.strange.spring.data.mongo.template

import com.strange.common.page.PageWindow
import com.strange.spring.error.ApiException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Query

/**
 * What a caller asks for: a direction, a size, where to resume from, and the query to page through.
 *
 * [first] pages forward from [cursor], [last] pages backward from it, and neither means the whole
 * result set. Asking for both is a contradiction and is refused.
 *
 * The three window fields and the rules behind them are `stx-common`'s [PageWindow], because
 * `stx-mongo` and `stx-jpa` ask for a page with the same three. What stays here is the Spring Data
 * [Query] and [Sort], and the failure — which is an [ApiException] rather than a family of its own,
 * since every way this goes wrong is a client sending something it should not have: a contradictory
 * window, a page size of zero, a cursor from a different query. Those are 400s, and they arrive
 * translated like every other failure this module raises.
 */
data class MongoPage(
    override val first: Int? = null,
    override val last: Int? = null,
    override val cursor: String? = null,
    val query: Query = Query(),
    val sort: Sort = Sort.unsorted(),
) : PageWindow {
    init {
        check { reason -> invalidPage(reason) }
    }

    companion object {
        /** Forward, [size] at a time, from [cursor] — the start of the result set when it is null. */
        fun first(
            size: Int,
            cursor: String? = null,
            query: Query = Query(),
            sort: Sort = Sort.unsorted(),
        ): MongoPage = MongoPage(first = size, cursor = cursor, query = query, sort = sort)

        /** Backward, [size] at a time, from [cursor] — the end of the result set when it is null. */
        fun last(
            size: Int,
            cursor: String? = null,
            query: Query = Query(),
            sort: Sort = Sort.unsorted(),
        ): MongoPage = MongoPage(last = size, cursor = cursor, query = query, sort = sort)
    }
}

/** What a page request nobody can honour is reported as. */
const val KEY_INVALID_PAGE = "pagination.invalid"

internal fun invalidPage(reason: String): ApiException =
    ApiException.badRequest(KEY_INVALID_PAGE, mapOf("reason" to reason), debugMessage = reason)
