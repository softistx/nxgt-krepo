package com.strange.jpa.criteria

import com.strange.jpa.query.criteria
import org.hibernate.query.criteria.JpaCriteriaDelete
import org.hibernate.query.criteria.JpaCriteriaInsertSelect
import org.hibernate.query.criteria.JpaCriteriaInsertValues
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.hibernate.query.criteria.JpaCriteriaUpdate
import org.hibernate.reactive.stage.Stage

/**
 * A criteria `select`, typed by [R] rather than by a class literal.
 *
 * ```kotlin
 * val criteria = session.createQuery<Purchase>()
 * val purchase = criteria.from(Purchase::class.java)
 * val buyer = purchase.fetch(Purchase::customer)
 * criteria.where(buyer[Buyer::name] eq "ada")
 *
 * session.query(criteria).list()
 * ```
 *
 * JPA spells this `session.criteria.createQuery(Purchase::class.java)`. Reifying it drops the
 * `.java` and, more usefully, lets the result type come from the variable's own type rather than
 * from a literal that can quietly stop matching it.
 *
 * `Stage.QueryProducer` is the receiver, so a session and a stateless one get it identically. The
 * statement is not bound to the session that made it — [Stage.SessionFactory.createQuery] builds the
 * same thing without opening one, for a criteria assembled at startup and run on every request.
 *
 * Building it is not running it: [com.strange.jpa.query.query] and [com.strange.jpa.query.mutate]
 * are what hand it to the session and give back this module's suspending terminals.
 */
inline fun <reified R : Any> Stage.QueryProducer.createQuery(): JpaCriteriaQuery<R> = criteria.createQuery(R::class.java)

/** A criteria `update` over [E]. Run it with [com.strange.jpa.query.mutate]. */
inline fun <reified E : Any> Stage.QueryProducer.createUpdate(): JpaCriteriaUpdate<E> = criteria.createCriteriaUpdate(E::class.java)

/** A criteria `delete` over [E], the same way. */
inline fun <reified E : Any> Stage.QueryProducer.createDelete(): JpaCriteriaDelete<E> = criteria.createCriteriaDelete(E::class.java)

/** A criteria `insert … values` into [E]. */
inline fun <reified E : Any> Stage.QueryProducer.createInsert(): JpaCriteriaInsertValues<E> =
    criteria.createCriteriaInsertValues(E::class.java)

/** A criteria `insert … select` into [E] — the rows come from a query rather than from literals. */
inline fun <reified E : Any> Stage.QueryProducer.createInsertSelect(): JpaCriteriaInsertSelect<E> =
    criteria.createCriteriaInsertSelect(E::class.java)

/**
 * The same `select`, built off the factory instead of a session.
 *
 * A criteria is a value: nothing about it needs a connection, and a statement assembled once at
 * startup can be handed to a session per request. `Stage.SessionFactory` declares the builder
 * itself, which is why this needs no session to exist.
 */
inline fun <reified R : Any> Stage.SessionFactory.createQuery(): JpaCriteriaQuery<R> = criteriaBuilder.createQuery(R::class.java)

/** A criteria `update` over [E], built off the factory. */
inline fun <reified E : Any> Stage.SessionFactory.createUpdate(): JpaCriteriaUpdate<E> = criteriaBuilder.createCriteriaUpdate(E::class.java)

/** A criteria `delete` over [E], built off the factory. */
inline fun <reified E : Any> Stage.SessionFactory.createDelete(): JpaCriteriaDelete<E> = criteriaBuilder.createCriteriaDelete(E::class.java)

/** A criteria `insert … values` into [E], built off the factory. */
inline fun <reified E : Any> Stage.SessionFactory.createInsert(): JpaCriteriaInsertValues<E> =
    criteriaBuilder.createCriteriaInsertValues(E::class.java)

/** A criteria `insert … select` into [E], built off the factory. */
inline fun <reified E : Any> Stage.SessionFactory.createInsertSelect(): JpaCriteriaInsertSelect<E> =
    criteriaBuilder.createCriteriaInsertSelect(E::class.java)
