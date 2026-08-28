package com.strange.jpa.page

import kotlin.reflect.KProperty1

/**
 * One field of the ordering a page is cut along, and which way it runs.
 *
 * It holds the property rather than a path, because a cursor is the sort key of the row it points
 * at and the value has to be read back off the row that was returned. `KProperty1.get` does that
 * with no `kotlin-reflect` on the classpath — a property reference compiles to a class with a getter
 * — which is the same reason the query DSL is built on property references in the first place.
 */
class SortKey<T : Any> internal constructor(
    internal val property: KProperty1<T, Comparable<*>>,
    internal val ascending: Boolean,
) {
    internal val name: String get() = property.name

    internal fun valueOf(row: T): Comparable<*> = property.get(row)
}
