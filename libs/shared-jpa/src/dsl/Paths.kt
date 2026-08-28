package com.strange.jpa.dsl

import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Path
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import kotlin.reflect.KProperty1

/**
 * Somewhere paths hang off — the query's root, or a join taken from it.
 *
 * ```kotlin
 * val customer = join(Order::customer)
 * where { (this[Order::total] gt 100L) and (customer[Customer::name] eq "ada") }
 * ```
 *
 * **Indexing rather than `Order::total eq 100L`, and the reason is not taste.** `KProperty1<T, V>`
 * is covariant in `V`, so the compiler is free to widen `V` to `Any` and `Order::total eq "nope"`
 * type-checks against a `Long` column — the mistake the DSL exists to catch, passed straight through
 * to a runtime failure inside Hibernate. `Path<V>` is invariant, so once the property has been
 * turned into one the value has nowhere to widen to and the same line is a compile error. Kotlin's
 * standard library solves this with `@OnlyInputTypes`, which is internal to it.
 *
 * The same indexing works on a join, which is the other half of the reason: one idiom, whether the
 * attribute belongs to the entity being selected or to something it points at.
 */
@JpaDsl
sealed interface Paths<T : Any> {
    /** The root or join the paths are taken from. */
    val from: From<*, T>

    /**
     * Hibernate's builder, for everything this package has not given a name.
     *
     * Taken off the root rather than carried alongside it, so every scope has one without being
     * handed one — including a join, which is only ever built from something that already had it.
     */
    val builder: HibernateCriteriaBuilder get() = from.builder

    /** The path to an attribute — the DSL's primitive, and the only typed way in. */
    operator fun <V> get(property: KProperty1<T, V>): Path<V> = from.get(property.name)

    /**
     * Ascending by an attribute, so an ordering reads like a restriction does.
     *
     * `orderBy { asc(Purchase::total) }`. The top-level [asc] takes an expression and is the one for
     * a join's column or a function's result — `asc(lower(this[Buyer::name]))`.
     */
    fun asc(property: KProperty1<T, *>): Order = builder.asc(this[property])

    /** Descending by an attribute. */
    fun desc(property: KProperty1<T, *>): Order = builder.desc(this[property])
}
