package com.strange.jpa.page

import com.strange.jpa.dsl.JpaDsl
import com.strange.jpa.dsl.QueryScope
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import kotlin.reflect.KProperty1

/**
 * The `selectPage { }` block: the same restrictions and joins as any query, and an ordering said
 * with [sortBy] rather than with `orderBy`.
 *
 * ```kotlin
 * session.selectPage<Purchase>(PageRequest.first(20)) {
 *     where { this[Purchase::total] gt 100L }
 *     sortBy(Purchase::total, descending = true)
 *     sortBy(Purchase::id)
 * }
 * ```
 *
 * **`sortBy` and not `orderBy`, and the difference is the cursor.** A cursor is the sort key of the
 * row it points at, so the page has to be able to read those values back off the row; an `orderBy`
 * takes an expression, and there is no way back from an expression to a value. `sortBy` takes the
 * property, which reads. A block that uses `orderBy` anyway is refused rather than quietly paged
 * along a key its cursors do not carry.
 */
@JpaDsl
class PageScope<T : Any>
    @PublishedApi
    internal constructor(
        query: CriteriaQuery<T>,
        from: Root<T>,
    ) : QueryScope<T, T>(query, from) {
        internal val keys = mutableListOf<SortKey<T>>()

        /**
         * Adds a sort key, after any already added.
         *
         * The last one has to be the entity's identifier — see [selectPage] for why a page with no
         * unique last key is refused rather than cut.
         */
        fun <V : Comparable<in V>> sortBy(
            property: KProperty1<T, V>,
            descending: Boolean = false,
        ) {
            keys += SortKey(property, ascending = !descending)
        }
    }
