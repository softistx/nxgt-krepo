package com.strange.common.page

/**
 * What a caller asks for, minus the part that is the store's own: a direction, a size, and where to
 * resume from.
 *
 * Every keyset-paginated store in this repo takes the same three fields and derives the same two
 * answers from them, so they live here once. What does *not* live here is the type a caller writes:
 * `shared-mongo` carries a filter and sort as raw Mongo JSON and is `@Serializable`, `shared-jpa`
 * says both in a Kotlin block, and each throws from its own sealed exception family — a caller
 * catching that family should not have to also catch this module's. So each store keeps its own
 * data class and implements this, rather than being handed one.
 *
 * [check] is the validation, called from the implementing class's `init` with its own exception:
 *
 * ```kotlin
 * init { check { PaginationException(it) } }
 * ```
 */
interface PageWindow {
    /** Page forward from [cursor], at most this many. Null and [last] null means the whole set. */
    val first: Int?

    /** Page backward from [cursor], at most this many. Contradicts [first]. */
    val last: Int?

    /** The row to resume from, or null for the start of the result set — the end when paging back. */
    val cursor: String?

    /** How many rows this page holds at most, or null when the caller wants all of them. */
    val limit: Int? get() = first ?: last

    /** Forward is the default; only an explicit [last] pages backward. */
    val forward: Boolean get() = last == null

    /**
     * Refuses a contradictory or impossible window, through the implementing store's own exception.
     *
     * Not `init` and not a `require`, because the whole point is that the caller catches one family.
     */
    fun check(failure: (String) -> Throwable) {
        if (first != null && last != null) {
            throw failure("first and last cannot both be set: pick a direction")
        }
        val limit = limit
        if (limit != null && limit < 1) {
            throw failure("a page size must be at least 1, got $limit")
        }
    }
}
