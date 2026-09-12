package com.softistx.common.page

import kotlinx.serialization.Serializable

/** One page of results and the cursors needed to ask for the next or previous one. */
@Serializable
data class Page<T>(
    val data: List<T>,
    val info: PageInfo,
) {
    /**
     * The whole reason this is not a bare `List` at the boundary: a route maps entities to
     * responses, and the cursors have to survive that untouched.
     */
    fun <R> map(transform: (T) -> R): Page<R> = Page(data.map(transform), info)

    companion object {
        fun <T> empty(): Page<T> = Page(emptyList(), PageInfo())
    }
}
