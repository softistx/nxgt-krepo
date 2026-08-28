package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder

/**
 * The `update { }` block: what is assigned, and to which rows.
 *
 * ```kotlin
 * session.update<Purchase> {
 *     this[Purchase::total] set 0L
 *     this[Purchase::reference] set "void"
 *     where { this[Purchase::customer].isNull() }
 * }.execute()
 * ```
 *
 * An assignment can be an expression rather than a value, which is how a counter is incremented
 * without reading it first — `this[Purchase::total] set (this[Purchase::total] + 1)` is one
 * statement and one round trip, and it is correct under concurrency in a way that read-modify-write
 * is not.
 *
 * It cannot join. That is JPA's rule for a bulk statement, not this module's, which is why the scope
 * is a [Paths] and not a [Joins] — a join here would compile and then fail when Hibernate rendered
 * it.
 */
@JpaDsl
class UpdateScope<T : Any>
    @PublishedApi
    internal constructor(
        /** Hibernate's builder, for everything this package has not given a name. */
        val builder: HibernateCriteriaBuilder,
        @PublishedApi internal val statement: CriteriaUpdate<T>,
        override val from: Root<T>,
    ) : Paths<T> {
        private val restrictions = mutableListOf<Predicate>()
        private var assignments = 0
        private var unrestricted = false

        /** Assigns a value. */
        infix fun <V> Path<V>.set(value: V) {
            statement.set(this, value)
            assignments++
        }

        /** Assigns an expression — another column, the same column plus one, a function's result. */
        infix fun <V> Path<V>.set(value: Expression<out V>) {
            statement.set(this, value)
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
