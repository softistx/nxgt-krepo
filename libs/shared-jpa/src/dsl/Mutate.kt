package com.strange.jpa.dsl

import com.strange.jpa.JpaUnrestrictedMutationException
import com.strange.jpa.query.JpaMutation
import org.hibernate.reactive.stage.Stage

/**
 * A bulk `update` built from the entity's own properties.
 *
 * ```kotlin
 * jpa.transaction { session ->
 *     session
 *         .update<Purchase> {
 *             this[Purchase::total] set (this[Purchase::total] + 10L)
 *             where { this[Purchase::reference] like "P-%" }
 *         }.execute()
 * }
 * ```
 *
 * It answers with the same [JpaMutation] `mutate(hql)` does, and carries the same warning: it goes
 * straight to the database, past everything the session knows. No cascade fires, no `@PreUpdate`
 * runs, and an entity already loaded in this session keeps the values it had.
 *
 * A statement with nothing restricting it throws [JpaUnrestrictedMutationException] unless the block
 * said `everyRow()`.
 */
inline fun <reified T : Any> Stage.QueryProducer.update(block: UpdateScope<T>.() -> Unit): JpaMutation = updateOn(this, block)

/**
 * A bulk `delete`, the same way.
 *
 * ```kotlin
 * session.delete<Purchase> { where { this[Purchase::total] lt 1L } }.execute()
 * ```
 *
 * `Jpa.removeById` is the other way to delete, and the difference is not style: that one loads the
 * entity so the cascades and the `@PreRemove` fire, and costs a select per row. This is one
 * statement for the whole set and fires nothing.
 */
inline fun <reified T : Any> Stage.QueryProducer.delete(block: DeleteScope<T>.() -> Unit): JpaMutation = deleteOn(this, block)

/*
 * The two below take the session as an argument rather than as a receiver, and that is not a style
 * choice. `Stage.StatelessSession` has members called `update` and `delete` that take an entity, and
 * a member beats an extension of the same name — so inside `JpaStatelessSession`, `raw.update(block)`
 * compiles into `update(Object)` and hands back a `CompletionStage<Void>`. It is the same trap
 * `JpaSession` documents for `flush`, met from the other side. A name with no member to collide with
 * is the only thing that reliably reaches the extension.
 */

/** Builds the `update`, wherever it was called from. */
@PublishedApi
internal inline fun <reified T : Any> updateOn(
    producer: Stage.QueryProducer,
    block: UpdateScope<T>.() -> Unit,
): JpaMutation {
    val builder = producer.builder
    val statement = builder.createCriteriaUpdate(T::class.java)
    val scope = UpdateScope(builder, statement, statement.from(T::class.java)).apply(block)
    if (scope.isUnrestricted()) throw JpaUnrestrictedMutationException(T::class, "update")
    return JpaMutation(producer.createMutationQuery(scope.build()))
}

/** Builds the `delete`, wherever it was called from. */
@PublishedApi
internal inline fun <reified T : Any> deleteOn(
    producer: Stage.QueryProducer,
    block: DeleteScope<T>.() -> Unit,
): JpaMutation {
    val builder = producer.builder
    val statement = builder.createCriteriaDelete(T::class.java)
    val scope = DeleteScope(builder, statement, statement.from(T::class.java)).apply(block)
    if (scope.isUnrestricted()) throw JpaUnrestrictedMutationException(T::class, "delete")
    return JpaMutation(producer.createMutationQuery(scope.build()))
}
