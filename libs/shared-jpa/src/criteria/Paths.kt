package com.strange.jpa.criteria

import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Path
import kotlin.reflect.KProperty1

/**
 * The attribute, named by the property rather than by a string.
 *
 * ```kotlin
 * val criteria = session.criteria.createQuery(Book::class.java)
 * val author = criteria.from(Author::class.java)
 * criteria.where(author[Author::name] oneOf listOf("Neal Stephenson", "William Gibson"))
 * ```
 *
 * This is the static metamodel, as close as this toolchain gets to one: `author[Author::name]` where
 * a generated `Author_` would have said `author.get(Author_.name)`, checked by the compiler the same
 * way and renamed by the IDE the same way — and with nothing generated, which matters because
 * `hibernate-jpamodelgen` is a javac processor that cannot run over Kotlin sources here.
 *
 * It chains across a to-one association the way Criteria does, implicitly joining:
 * `purchase[Purchase::customer][Buyer::name]`. The receiver is projected so that step works even
 * though the association is nullable in Kotlin, which a to-one usually is.
 *
 * Every operator in this package takes an [jakarta.persistence.criteria.Expression], and a path is
 * one — so `eq`, `gt`, `oneOf`, `like` and the rest apply to it directly.
 */
operator fun <T : Any, V> Path<out T?>.get(property: KProperty1<T, V>): Path<V> = get<V>(property.name)

/**
 * Joins a to-one association, named by the property.
 *
 * ```kotlin
 * val purchase = criteria.from(Purchase::class.java)
 * val buyer = purchase.join(Purchase::customer, JoinType.LEFT)
 * ```
 *
 * The property may be nullable — a to-one association usually is in Kotlin, and an inner join over
 * one is how a query says *only the ones that have a customer*. The join is on the entity either
 * way, so the nullability is dropped from what comes back.
 *
 * Nothing is remembered: two calls are two joins, as they are in Criteria itself, and two joins to
 * the same association are two joins in the SQL. Hold it in a `val` — which is the shape a criteria
 * has anyway, since the join is what the later paths hang off.
 */
fun <T : Any, V : Any> From<*, T>.join(
    property: KProperty1<T, V?>,
    type: JoinType = JoinType.INNER,
): Join<T, V> = join(property.name, type)

/**
 * Joins a to-many association, once per element.
 *
 * The element type comes out of `KProperty1<T, Collection<E>>` with no reflection at runtime —
 * the compiler already knows what a `List<Book>` holds.
 */
fun <T : Any, E : Any> From<*, T>.joinEach(
    property: KProperty1<T, Collection<E>>,
    type: JoinType = JoinType.INNER,
): Join<T, E> = join(property.name, type)
