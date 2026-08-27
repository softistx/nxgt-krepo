package com.strange.jpa

import kotlin.reflect.KClass

/**
 * What this module throws that Hibernate does not.
 *
 * Each of these replaces a JPA exception that says what happened without saying to what. A
 * `NoResultException` carrying no query text, thrown out of a `CompletionStage` whose stack is a
 * chain of Vert.x frames, is close to unactionable; the same failure with the HQL on it is a
 * one-line fix. Everything else Hibernate throws travels through untouched — this is a hierarchy for
 * the few cases where we know something it does not, not a wrapper around its own.
 */
sealed class JpaException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * An entity the caller said must exist, by an id the database has no row for.
 *
 * The counterpart of `find`, which answers null. Which one to reach for is a question about the
 * caller, not the data: a handler resolving a path parameter wants the throw, a lookup that has a
 * fallback wants the null.
 */
class JpaNotFoundException(
    val type: KClass<*>,
    val id: Any,
) : JpaException("no ${type.simpleName} with id $id")

/** A `single()` that matched no row. Use `singleOrNull()` when none is an ordinary answer. */
class JpaNoResultException(
    val hql: String,
    cause: Throwable? = null,
) : JpaException("the query matched nothing: $hql", cause)

/**
 * A `single()` or `singleOrNull()` that matched more than one row.
 *
 * Never an ordinary answer, and never silently narrowed to the first: a query that was written
 * expecting one row and found two has a wrong `where` clause or a wrong assumption about a
 * uniqueness constraint, and taking the first hides both.
 */
class JpaNonUniqueResultException(
    val hql: String,
    cause: Throwable? = null,
) : JpaException("the query matched more than one row: $hql", cause)
