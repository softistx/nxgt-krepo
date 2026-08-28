package com.strange.jpa.dsl

import jakarta.persistence.criteria.JoinType
import kotlin.reflect.KProperty1

/**
 * Paths that can be joined from — a selection, or a join already taken.
 *
 * ```kotlin
 * session
 *     .select<Purchase>()
 *     .where { join(Purchase::customer)[Buyer::name] eq "ada" }
 *     .orderBy { asc(join(Purchase::customer)[Buyer::id]) }
 * ```
 *
 * **Asking for the same join twice gives the same join, not a second one.** They are remembered by
 * attribute, so a join can be taken where it is used rather than declared ahead of everything that
 * uses it — which is what lets `where` and `orderBy` be chained onto the query instead of living in
 * a block. Holding it in a `val` still reads better when it is used several times, and now means the
 * same thing.
 *
 * Separate from [Filters] because a bulk `update` or `delete` cannot join: JPA's `CriteriaUpdate`
 * hands out a `Root` like any other, and Hibernate refuses the join when it renders the statement. A
 * method that is always a runtime failure is better not offered, so the update and delete scopes are
 * [Filters] and stop there.
 */
@JpaDsl
sealed interface Joins<T : Any> : Filters<T> {
    /**
     * The joins taken so far, by attribute name — the DSL's own bookkeeping, which memoizes so that
     * asking for the same association twice gives back one join rather than two.
     *
     * Public because a sealed interface has nowhere else to put shared state: Kotlin allows neither
     * `internal` nor `protected` on an interface member, and the scopes that implement this already
     * have a superclass each. Nothing outside the DSL should touch it, and emptying it produces a
     * query with the duplicate join this exists to prevent — but the type system cannot say so here,
     * so the sentence has to.
     */
    val taken: MutableMap<String, JoinScope<T, *>>

    /**
     * Joins a to-one association, and answers with something to index.
     *
     * The property may be nullable — a to-one association usually is in Kotlin, and an inner join
     * over one is exactly how a query says *only the ones that have a customer*. The join is on the
     * entity either way, so the nullability is dropped from what comes back.
     */
    fun <V : Any> join(
        property: KProperty1<T, V?>,
        type: JoinType = JoinType.INNER,
    ): JoinScope<T, V> = joined(property.name, type)

    /**
     * Joins a to-many association, once per element.
     *
     * The element type comes out of `KProperty1<T, Collection<E>>` and needs no reflection at
     * runtime — the compiler already knows what a `List<Line>` holds. A row per element is what a
     * join means, so a query that selects the owning entity through one wants `distinct` unless it
     * wants duplicates.
     */
    fun <E : Any> joinEach(
        property: KProperty1<T, Collection<E>>,
        type: JoinType = JoinType.INNER,
    ): JoinScope<T, E> = joined(property.name, type)

    @Suppress("UNCHECKED_CAST")
    private fun <V : Any> joined(
        name: String,
        type: JoinType,
    ): JoinScope<T, V> {
        val existing = taken[name]
        if (existing != null) {
            // An IllegalStateException rather than a JpaException, deliberately: the family is
            // scoped to what this library knows and Hibernate does not about the *data*, and asking
            // for one association two ways in one query is a programming error like any other.
            check(existing.type == type) {
                "'$name' is already joined as ${existing.type} and this asks for $type: " +
                    "a join is taken once, and asking again gives back the one already taken"
            }
            return existing as JoinScope<T, V>
        }
        return JoinScope<T, V>(from.join(name, type), type).also { taken[name] = it }
    }
}
