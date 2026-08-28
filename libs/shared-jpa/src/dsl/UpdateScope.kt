package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

/**
 * A bulk `update`: what is assigned, and — through [MutationScope] — to which rows.
 *
 * ```kotlin
 * session
 *     .update<Purchase> {
 *         set(Purchase::total, 0L)
 *         set(Purchase::reference, "void")
 *     }.where { Purchase::customer.isNull() }
 *     .execute()
 * ```
 *
 * An assignment can be an expression rather than a value, which is how a counter is incremented
 * without reading it first — `set(Purchase::total, this[Purchase::total] + 1)` is one statement and
 * one round trip, and it is correct under concurrency in a way that read-modify-write is not.
 */
@JpaDsl
class UpdateScope<T : Any>
    @PublishedApi
    internal constructor(
        producer: Stage.QueryProducer,
        entity: KClass<T>,
        private val statement: CriteriaUpdate<T>,
        from: Root<T>,
    ) : MutationScope<T, UpdateScope<T>>(producer, entity, from) {
        private var assignments = 0

        override val verb: String get() = "update"

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
        ): UpdateScope<T> =
            apply {
                statement.set(this[property], value)
                assignments++
            }

        /** Assigns an expression — another column, the same column plus one, a function's result. */
        fun <V> set(
            property: KMutableProperty1<T, V>,
            value: Expression<out V>,
        ): UpdateScope<T> =
            apply {
                // The type argument is not decoration: JPA's `set(Path<Y>, X)` also accepts an
                // expression as a plain value, and without it the two overloads are ambiguous.
                statement.set<V>(this[property], value)
                assignments++
            }

        override fun query(restrictions: List<Predicate>): Stage.MutationQuery {
            require(assignments > 0) { "an update needs at least one set" }
            if (restrictions.isNotEmpty()) statement.where(*restrictions.toTypedArray())
            return producer.createMutationQuery(statement)
        }
    }
