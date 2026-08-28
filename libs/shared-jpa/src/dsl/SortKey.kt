package com.strange.jpa.dsl

import kotlin.reflect.KProperty1

/**
 * One field of an ordering, named by the property rather than by an expression.
 *
 * The property is kept, not just the path it becomes, because a cursor is the sort key of the row it
 * points at and the value has to be read back off the row that was returned — `KProperty1.get` does
 * that with no `kotlin-reflect` on the classpath. `orderBy` takes an expression and there is no way
 * back from one to a value, which is why paging needs [QueryScope.sortBy] and not `orderBy`.
 */
class SortKey<T : Any> internal constructor(
    internal val property: KProperty1<T, Comparable<*>>,
    internal val ascending: Boolean,
) {
    internal val name: String get() = property.name

    internal fun valueOf(row: T): Comparable<*> = property.get(row)
}
