package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import kotlin.reflect.KMutableProperty1

/**
 * The `update { }` block: what is assigned, and to which rows.
 *
 * ```kotlin
 * session.update<Purchase> {
 *     set(Purchase::total, 0L)
 *     set(Purchase::reference, "void")
 *     where { Purchase::customer.isNull() }
 * }.execute()
 * ```
 *
 * An assignment can be an expression rather than a value, which is how a counter is incremented
 * without reading it first — `set(Purchase::total, this[Purchase::total] + 1)` is one statement and
 * one round trip, and it is correct under concurrency in a way that read-modify-write is not.
 *
 * It cannot join. That is JPA's rule for a bulk statement, not this module's, which is why the scope
 * is a [Filters] and not a [Joins] — a join here would compile and then fail when Hibernate rendered
 * it.
 */
@JpaDsl
class UpdateScope<T : Any>
    @PublishedApi
    internal constructor(
        @PublishedApi internal val statement: CriteriaUpdate<T>,
        override val from: Root<T>,
    ) : Filters<T> {
        private val restrictions = mutableListOf<Predicate>()
        private var assignments = 0
        private var unrestricted = false

        /**
         * Assigns a value.
         *
         * The property is a `KMutableProperty1` for the reason [Filters] spells out: it is invariant
         * in the value, so `set(Purchase::total, "nope")` is a compile error rather than a failure
         * inside Hibernate.
         */
        fun <V> set(
            property: KMutableProperty1<T, V>,
            value: V,
        ) {
            statement.set(this[property], value)
            assignments++
        }

        /** Assigns an expression — another column, the same column plus one, a function's result. */
        fun <V> set(
            property: KMutableProperty1<T, V>,
            value: Expression<out V>,
        ) {
            // The type argument is not decoration: JPA's `set(Path<Y>, X)` also accepts an
            // expression as a plain value, and without it the two overloads are ambiguous.
            statement.set<V>(this[property], value)
            assignments++
        }

        /** Restricts the statement. Called more than once, the restrictions are `and`ed together. */
        fun where(block: UpdateScope<T>.() -> Predicate?) {
            block()?.let { restrictions += it }
        }

        /** Says that every row is meant, which is the only way to build a statement without a [where]. */
        fun everyRow() {
            unrestricted = true
        }

        @PublishedApi
        internal fun build(): CriteriaUpdate<T> {
            require(assignments > 0) { "an update needs at least one set" }
            if (restrictions.isNotEmpty()) statement.where(*restrictions.toTypedArray())
            return statement
        }

        @PublishedApi
        internal fun isUnrestricted(): Boolean = restrictions.isEmpty() && !unrestricted
    }
