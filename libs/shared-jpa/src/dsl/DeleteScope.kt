package com.strange.jpa.dsl

import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.hibernate.reactive.stage.Stage
import kotlin.reflect.KClass

/**
 * A bulk `delete`: which rows go.
 *
 * ```kotlin
 * session.delete<Purchase>().where { Purchase::total lt 1L }.execute()
 * ```
 *
 * `Jpa.removeById` is the other way to delete, and the difference is not style: that one loads the
 * entity so the cascades and the `@PreRemove` fire, and costs a select per row. This is one
 * statement for the whole set and fires nothing.
 */
@JpaDsl
class DeleteScope<T : Any>
    @PublishedApi
    internal constructor(
        producer: Stage.QueryProducer,
        entity: KClass<T>,
        private val statement: CriteriaDelete<T>,
        from: Root<T>,
    ) : MutationScope<T, DeleteScope<T>>(producer, entity, from) {
        override val verb: String get() = "delete"

        override fun query(restrictions: List<Predicate>): Stage.MutationQuery {
            if (restrictions.isNotEmpty()) statement.where(*restrictions.toTypedArray())
            return producer.createMutationQuery(statement)
        }
    }
