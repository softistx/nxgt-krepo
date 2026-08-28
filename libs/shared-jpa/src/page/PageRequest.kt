package com.strange.jpa.page

import com.strange.jpa.JpaPaginationException

/**
 * What a caller asks for: a direction, a size, and where to resume from.
 *
 * [first] pages forward from [cursor], [last] pages backward from it, and neither means the whole
 * result set. Asking for both is a contradiction and is rejected. The shape is deliberately the one
 * `shared-mongo` takes, minus the filter and sort it carries as raw Mongo JSON — here both are said
 * in the block, in Kotlin, and checked by the compiler.
 */
data class PageRequest(
    val first: Int? = null,
    val last: Int? = null,
    val cursor: String? = null,
) {
    /** How many rows this page holds at most, or null when the caller wants all of them. */
    val limit: Int? = first ?: last

    /** Forward is the default; only an explicit [last] pages backward. */
    val forward: Boolean = last == null

    init {
        if (first != null && last != null) {
            throw JpaPaginationException("first and last cannot both be set: pick a direction")
        }
        if (limit != null && limit < 1) {
            throw JpaPaginationException("a page size must be at least 1, got $limit")
        }
    }

    companion object {
        /** Forward, [size] at a time, from [cursor] — the start of the result set when it is null. */
        fun first(
            size: Int,
            cursor: String? = null,
        ): PageRequest = PageRequest(first = size, cursor = cursor)

        /** Backward, [size] at a time, from [cursor] — the end of the result set when it is null. */
        fun last(
            size: Int,
            cursor: String? = null,
        ): PageRequest = PageRequest(last = size, cursor = cursor)
    }
}
