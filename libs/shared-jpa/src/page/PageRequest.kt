package com.strange.jpa.page

import com.strange.common.page.PageWindow
import com.strange.jpa.JpaPaginationException

/**
 * What a caller asks for: a direction, a size, and where to resume from.
 *
 * [first] pages forward from [cursor], [last] pages backward from it, and neither means the whole
 * result set. Asking for both is a contradiction and is rejected.
 *
 * The three fields, [PageWindow.limit], [PageWindow.forward] and the rules are `shared-common`'s,
 * because `shared-mongo` asks for a page with the same three. What stays here is the type a caller
 * writes and the exception it throws: `shared-mongo` carries a filter and sort as raw Mongo JSON,
 * where both are said in the block here, in Kotlin, and checked by the compiler — and a caller
 * catching [com.strange.jpa.JpaException] should not have to also catch `shared-common`'s.
 */
data class PageRequest(
    override val first: Int? = null,
    override val last: Int? = null,
    override val cursor: String? = null,
) : PageWindow {
    init {
        check(::JpaPaginationException)
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
