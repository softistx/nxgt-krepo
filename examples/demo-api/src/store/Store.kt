package com.softistx.demo.api.store

import com.softistx.demo.api.model.PageInfo
import java.util.concurrent.atomic.AtomicLong

/**
 * Insertion-ordered in-memory storage, enough to exercise the generated client.
 *
 * Cursor pagination is keyed on the item id: `cursor` names the last item of the previous
 * page, and `first`/`last` take from the start or the end of what remains.
 */
internal class Store<T : Any>(
    private val idOf: (T) -> String,
) {
    private val items = LinkedHashMap<String, T>()

    fun all(): List<T> = synchronized(items) { items.values.toList() }

    fun find(id: String): T? = synchronized(items) { items[id] }

    fun put(item: T): T = synchronized(items) { item.also { items[idOf(it)] = it } }

    fun remove(id: String): Boolean = synchronized(items) { items.remove(id) != null }

    fun page(
        cursor: String?,
        first: Int?,
        last: Int?,
    ): Page<T> {
        val all = all()
        // An unknown cursor means the item it named is gone, so there is nothing after it.
        val start = if (cursor == null) 0 else all.indexOfFirst { idOf(it) == cursor } + 1
        if (cursor != null && start == 0) return Page(emptyList(), null, null, false, false)

        val rest = all.drop(start)
        val window =
            when {
                first != null -> rest.take(first)
                last != null -> rest.takeLast(last)
                else -> rest
            }
        // Positions, not `indexOf`: two items with equal contents would otherwise report the
        // index of whichever came first, and page flags would be wrong for both.
        val from = if (last != null && first == null) start + rest.size - window.size else start
        return Page(
            items = window,
            startCursor = window.firstOrNull()?.let(idOf),
            endCursor = window.lastOrNull()?.let(idOf),
            hasPreviousPage = from > 0,
            hasNextPage = from + window.size < all.size,
        )
    }
}

internal data class Page<T>(
    val items: List<T>,
    val startCursor: String?,
    val endCursor: String?,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
) {
    fun info(): PageInfo = PageInfo(startCursor, endCursor, hasPreviousPage, hasNextPage)
}

/** Ids are sequential so tests can assert on them; a real service would not do this. */
internal class Ids {
    private val next = AtomicLong(1)

    fun next(): String = next.getAndIncrement().toString()
}
