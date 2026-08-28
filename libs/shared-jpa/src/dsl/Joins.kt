package com.strange.jpa.dsl

import jakarta.persistence.criteria.JoinType
import kotlin.reflect.KProperty1

/**
 * Paths that can be joined from — a selection, or a join already taken.
 *
 * Separate from [Paths] because a bulk `update` or `delete` cannot join: JPA's `CriteriaUpdate`
 * hands out a `Root` like any other, and Hibernate refuses the join when it renders the statement.
 * A method that is always a runtime failure is better not offered, so the update and delete scopes
 * are [Paths] and stop there.
 */
@JpaDsl
sealed interface Joins<T : Any> : Paths<T> {
    /**
     * Joins a to-one association, and answers with something to index.
     *
     * The property may be nullable — a to-one association usually is in Kotlin, and an inner join
     * over one is exactly how a query says *only the ones that have a customer*. The join is on the
     * entity either way, so the nullability is dropped from what comes back.
     *
     * Held as a value rather than scoped to a lambda, so one join serves the `where`, the `orderBy`
     * and — from the next slice — the projection, instead of being re-declared and re-joined for
     * each. A second `join` of the same attribute is a second join in the SQL.
     */
    fun <V : Any> join(
        property: KProperty1<T, V?>,
        type: JoinType = JoinType.INNER,
    ): JoinScope<T, V> = JoinScope(from.join(property.name, type))

    /**
     * Joins a to-many association, once per element.
     *
     * The element type comes out of `KProperty1<T, out Collection<E>>` and needs no reflection at
     * runtime — the compiler already knows what a `List<Line>` holds. A row per element is what a
     * join means, so a query that selects the owning entity through one wants [SelectScope.distinct]
     * unless it wants duplicates.
     */
    fun <E : Any> joinEach(
        property: KProperty1<T, out Collection<E>>,
        type: JoinType = JoinType.INNER,
    ): JoinScope<T, E> = JoinScope(from.join(property.name, type))
}
