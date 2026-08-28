package com.strange.jpa.session

import com.strange.jpa.query.JpaMutation
import com.strange.jpa.query.JpaQuery
import com.strange.jpa.query.criteria
import com.strange.jpa.query.mutate
import com.strange.jpa.query.nativeMutate
import com.strange.jpa.query.query
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaInsert
import org.hibernate.reactive.stage.Stage
import org.intellij.lang.annotations.Language

/**
 * What a session and a stateless session answer identically, said once.
 *
 * Both wrappers offer the same query vocabulary over the same `Stage.QueryProducer`, and every
 * member here used to exist twice, character for character. That is not a cosmetic problem: eleven
 * of the twelve commits that ever touched `JpaStatelessSession` also touched `JpaSession`, because
 * every change had to be made in both places with nothing to notice when it was not.
 *
 * **Default implementations rather than extension functions, deliberately.** As extensions these
 * would need an import at every call site and would lose to any member of the same name — the trap
 * `Mutate.kt` documents, and the reason `updateOn`/`deleteOn` are spelled the way they are. Inherited
 * members keep `session.mutate(hql)` compiling exactly as before, in this repo and downstream.
 *
 * **The four `reified` members are not here, and cannot be.** `query`, `select`, `project` and
 * `nativeQuery` each need the type argument as a `Class` before Hibernate sees it, so they are
 * `inline` — and an interface member is open, which `inline` forbids. Those four stay declared on
 * both classes; they are the residue this interface cannot absorb rather than an oversight.
 */
interface JpaQueries {
    /** The Hibernate Reactive session underneath — a `Stage.Session` or a `Stage.StatelessSession`. */
    val raw: Stage.QueryProducer

    /**
     * Hibernate's criteria builder, for a query written against the Criteria API directly.
     *
     * The way out of the DSL, for the queries it has no spelling for — subqueries, set operations,
     * window functions, `insert … select`. What comes back runs through [query] or [mutate], so a
     * criteria built by hand still ends in a suspending terminal rather than a `CompletionStage`.
     */
    val criteria: HibernateCriteriaBuilder get() = raw.criteria

    /** A criteria query, run through this module's terminals. */
    fun <R> query(criteria: CriteriaQuery<R>): JpaQuery<R> = raw.query(criteria)

    /** A criteria `update`. */
    fun mutate(criteria: CriteriaUpdate<*>): JpaMutation = raw.mutate(criteria)

    /** A criteria `delete`. */
    fun mutate(criteria: CriteriaDelete<*>): JpaMutation = raw.mutate(criteria)

    /** A criteria `insert` — `insert … select`, or `insert … values`. */
    fun mutate(criteria: JpaCriteriaInsert<*>): JpaMutation = raw.mutate(criteria)

    /** A bulk HQL `update` or `delete`. */
    fun mutate(
        @Language("HQL") hql: String,
    ): JpaMutation = raw.mutate(hql)

    /** The same in SQL. */
    fun nativeMutate(
        @Language("SQL") sql: String,
    ): JpaMutation = raw.nativeMutate(sql)
}
