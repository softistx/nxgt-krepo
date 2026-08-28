package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.query.criteria.HibernateCriteriaBuilder

/**
 * The `delete { }` block: which rows go.
 *
 * ```kotlin
 * session.delete<Purchase> {
 *     where { this[Purchase::total] lt 1L }
 * }.execute()
 * ```
 *
 * Like [UpdateScope] it cannot join, and like it, a statement with nothing restricting it has to say
 * [everyRow] out loud.
 */
@JpaDsl
class DeleteScope<T : Any>
    @PublishedApi
    internal constructor(
        /** Hibernate's builder, for everything this package has not given a name. */
        val builder: HibernateCriteriaBuilder,
        @PublishedApi internal val statement: CriteriaDelete<T>,
        override val from: Root<T>,
    ) : Paths<T> {
        private val restrictions = mutableListOf<Predicate>()
        private var unrestricted = false

        /** Restricts the statement. Called more than once, the restrictions are `and`ed together. */
        fun where(block: DeleteScope<T>.() -> Predicate?) {
            block()?.let { restrictions += it }
        }

        /** Says that every row is meant, which is the only way to build a statement without a [where]. */
        fun everyRow() {
            unrestricted = true
        }

        @PublishedApi
        internal fun build(): CriteriaDelete<T> {
            if (restrictions.isNotEmpty()) statement.where(*restrictions.toTypedArray())
            return statement
        }

        @PublishedApi
        internal fun isUnrestricted(): Boolean = restrictions.isEmpty() && !unrestricted
    }
