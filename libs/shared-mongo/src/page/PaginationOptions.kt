package com.strange.mongo.page

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
 */
@Serializable
data class PaginationOptions(
    val first: Int? = null,
    val last: Int? = null,
    val cursor: String? = null,
    val sort: JsonObject? = null,
    val filter: JsonObject? = null,
) {
    /** How many documents this page holds at most, or null when the caller wants all of them. */
    val limit: Int? = first ?: last

    /** Forward is the default; only an explicit [last] pages backward. */
    val forward: Boolean = last == null

    init {
        if (first != null && last != null) {
            throw InvalidPaginationException("first and last cannot both be set: pick a direction")
        }
        if (limit != null && limit < 1) {
            throw InvalidPaginationException("a page size must be at least 1, got $limit")
        }
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
