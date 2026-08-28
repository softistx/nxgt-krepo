package com.strange.jpa.dsl

import jakarta.persistence.criteria.Expression
import org.intellij.lang.annotations.Language

/**
 * A value as an expression, for the places one is wanted and a literal is what there is.
 *
 * Hibernate renders a criteria literal as a bound parameter, not as text in the statement — which is
 * why [sql] can take arguments at all without becoming an injection.
 */
fun <V : Any> Paths<*>.literal(value: V): Expression<V> = builder.literal(value)

/** A typed null, for the same places. */
inline fun <reified V : Any> Paths<*>.nullLiteral(): Expression<V> = builder.nullLiteral(V::class.javaObjectType)

/**
 * A database function this DSL has not named.
 *
 * ```kotlin
 * where { function<Double>("similarity", this[Buyer::name], literal(term)) gt 0.3 }
 * ```
 *
 * The name is passed to Hibernate, which knows the ones its dialect registers and renders the rest
 * as written. The return type is the caller's claim about what comes back and nothing checks it, so
 * it is the one place in this DSL where being wrong is a runtime failure.
 */
inline fun <reified V : Any> Paths<*>.function(
    name: String,
    vararg arguments: Expression<*>,
): Expression<V> = builder.function(name, V::class.javaObjectType, *arguments)

/**
 * A fragment of SQL, dropped into the query with its arguments bound.
 *
 * ```kotlin
 * where { sql<Boolean>("? % ?", this[Buyer::name], literal(term)) eq true }
 * where { sql<Boolean>("jsonb_exists(?, ?)", this[Purchase::document], literal("note")) eq true }
 * ```
 *
 * **`?` is a bind parameter, not a hole to interpolate into.** The trailing arguments are bound the
 * way any other parameter is, so a value carrying a quote is a value and not a second statement.
 * That is what makes this an escape hatch rather than a hazard, and it is why the fragment itself
 * should never be built by concatenating anything a caller supplied.
 *
 * This is Hibernate's own `sql()`, registered for every dialect it supports — not something this
 * module renders. What the fragment may say is therefore the database's business: trigram operators,
 * `jsonb` paths, a window function HQL has no syntax for. What it cannot do is know about the
 * mapping, so column names in the fragment are the database's names and not the entity's.
 */
inline fun <reified V : Any> Paths<*>.sql(
    // A fragment, not a statement: `prefix` gives it somewhere to sit so the IDE parses `? % ?`
    // as an expression instead of reporting an incomplete `select`.
    @Language("SQL", prefix = "select ", suffix = "") fragment: String,
    vararg arguments: Expression<*>,
): Expression<V> = function("sql", literal(fragment.checkedAgainst(arguments.size)), *arguments)

/**
 * Refuses a fragment whose placeholders and arguments do not match.
 *
 * Hibernate would refuse it too, later and less clearly — a missing argument surfaces as *no
 * argument for ordinal parameter '?1'* from somewhere inside the binder, with no mention of the
 * fragment that asked for it.
 */
@PublishedApi
internal fun String.checkedAgainst(arguments: Int): String {
    val placeholders = count { it == '?' }
    check(placeholders == arguments) {
        "the fragment has $placeholders placeholders and $arguments arguments: $this"
    }
    return this
}
