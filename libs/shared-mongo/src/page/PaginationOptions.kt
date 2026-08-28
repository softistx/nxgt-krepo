package com.strange.mongo.page

import com.strange.common.page.PageWindow
import com.strange.mongo.InvalidPaginationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * What a caller asks for: a direction, a size, where to resume from, and the filter and sort to
 * apply.
 *
 * [filter] and [sort] are raw Mongo JSON — `{"status": "ACTIVE"}` and `{"name": 1, "_id": -1}` —
 * because they arrive that way from an HTTP client and translating them into a typed DSL only to
 * translate them back is work nobody asked for. They are parsed, not concatenated, so a malformed
 * one fails as [InvalidPaginationException] rather than reaching the server.
 *
 * [first] pages forward from [cursor], [last] pages backward from it, and neither means the whole
 * result set. Asking for both is a contradiction and is rejected.
 *
 * Those three fields, [PageWindow.limit], [PageWindow.forward] and the rules are `shared-common`'s,
 * because `shared-jpa` asks for a page with the same three. What stays here is [sort], [filter],
 * the serialization, and the exception — a caller catching [com.strange.mongo.MongoDataException]
 * should not have to also catch `shared-common`'s.
 */
@Serializable
data class PaginationOptions(
    override val first: Int? = null,
    override val last: Int? = null,
    override val cursor: String? = null,
    val sort: JsonObject? = null,
    val filter: JsonObject? = null,
) : PageWindow {
    init {
        check(::InvalidPaginationException)
    }

    companion object {
        /** Forward, [size] at a time, from the start. */
        fun first(
            size: Int,
            cursor: String? = null,
        ): PaginationOptions = PaginationOptions(first = size, cursor = cursor)

        /** Backward, [size] at a time, from [cursor] — the end of the result set when it is null. */
        fun last(
            size: Int,
            cursor: String? = null,
        ): PaginationOptions = PaginationOptions(last = size, cursor = cursor)
    }
}
