package com.strange.jpa

import java.lang.reflect.Type
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

/**
 * A JSON column whose type kotlinx.serialization has no serializer for.
 *
 * Nearly always a missing `@Serializable`, and worth its own exception because Hibernate's version of
 * this failure arrives from inside a binder as kotlinx's "serializer for class … not found" and reads
 * like a bug in this library rather than a missing annotation on the caller's own class.
 */
class JpaSerializerException(
    val type: Type,
) : JpaException("no serializer for $type: a JSON column needs a @Serializable type")

/**
 * A stored JSON document that does not decode into the attribute it was read for.
 *
 * The document itself is deliberately not in the message. A stored value is somebody's payment
 * details as often as it is a test fixture, and an exception message ends up in a log nobody meant to
 * make sensitive — the same rule `decodeValue` in `shared-common` is written around.
 */
class JpaDocumentException(
    val type: Type,
    cause: Throwable,
) : JpaException("the stored JSON is not a $type", cause)

/**
 * A mapping or a bootstrap this library refuses before anything connects.
 *
 * These are the checks that exist because Hibernate would otherwise accept the configuration and
 * fail later, somewhere unhelpful: a `kotlin.uuid.Uuid` identifier that would silently become a
 * `bytea` column, a `@JdbcTypeCode` that disagrees with what the type serializes to, a scan that
 * matched no entity. They are the module's best diagnostics, and until now a caller wiring this up
 * in a Ktor `install` or a Koin `single { }` could not catch them as ours — they arrived as bare
 * `IllegalStateException`, indistinguishable from any other.
 */
class JpaMappingException(
    message: String,
) : JpaException(message)
