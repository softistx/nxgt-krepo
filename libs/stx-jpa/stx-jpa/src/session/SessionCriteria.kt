package com.softistx.jpa.session

import com.softistx.jpa.criteria.createDelete
import com.softistx.jpa.criteria.createInsert
import com.softistx.jpa.criteria.createInsertSelect
import com.softistx.jpa.criteria.createQuery
import com.softistx.jpa.criteria.createUpdate
import com.softistx.jpa.criteria.entityGraph
import jakarta.persistence.EntityGraph
import org.hibernate.query.criteria.JpaCriteriaDelete
import org.hibernate.query.criteria.JpaCriteriaInsertSelect
import org.hibernate.query.criteria.JpaCriteriaInsertValues
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.hibernate.query.criteria.JpaCriteriaUpdate

/**
 * The reified statement builders and fetch plans, on the wrappers rather than on `Stage`.
 *
 * ```kotlin
 * jpa.transaction { session ->
 *     val criteria = session.createQuery<Purchase>()
 *     val purchase = criteria.from(Purchase::class.java)
 *     criteria.where(purchase.fetch(Purchase::customer)[Buyer::name] eq "ada")
 *
 *     session.query(criteria).list()
 * }
 * ```
 *
 * Extensions on [JpaQueries] and not members of it, because each one needs its type argument as a
 * `Class` before Hibernate sees it — so each is `inline` and `reified`, and an interface member is
 * open, which `inline` forbids. Written once against the interface, they answer for a session and a
 * stateless one alike, which is what the interface exists for.
 */
inline fun <reified R : Any> JpaQueries.createQuery(): JpaCriteriaQuery<R> = raw.createQuery()

/** A criteria `update` over [E]. Run it with [JpaQueries.mutate]. */
inline fun <reified E : Any> JpaQueries.createUpdate(): JpaCriteriaUpdate<E> = raw.createUpdate()

/** A criteria `delete` over [E], the same way. */
inline fun <reified E : Any> JpaQueries.createDelete(): JpaCriteriaDelete<E> = raw.createDelete()

/** A criteria `insert … values` into [E]. */
inline fun <reified E : Any> JpaQueries.createInsert(): JpaCriteriaInsertValues<E> = raw.createInsert()

/** A criteria `insert … select` into [E]. */
inline fun <reified E : Any> JpaQueries.createInsertSelect(): JpaCriteriaInsertSelect<E> = raw.createInsertSelect()

/**
 * An empty fetch plan for [T] — see [com.softistx.jpa.criteria.entityGraph] for what to fill it with.
 *
 * A plan is a value and not a query, so the one built here can be handed to `find`, to a stateless
 * `get`, and to as many queries' `plan` as want it.
 */
inline fun <reified T : Any> JpaQueries.entityGraph(): EntityGraph<T> = raw.entityGraph()
