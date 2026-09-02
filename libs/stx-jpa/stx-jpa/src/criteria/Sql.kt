package com.softistx.jpa.criteria

import jakarta.persistence.criteria.Expression
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.intellij.lang.annotations.Language

/**
 * A typed null, for the places an expression is wanted and there is no value.
 *
 * JPA spells this `nullLiteral(String::class.java)`; reifying it drops the `.java` and keeps the
 * type where the compiler can see it. A plain literal needs nothing added — `builder.literal(value)`
 * is already typed, and Hibernate renders it as a bound parameter rather than as text in the
 * statement, which is what lets [sql] take arguments at all without becoming an injection.
 */
inline fun <reified V : Any> HibernateCriteriaBuilder.nullLiteral(): Expression<V> = nullLiteral(V::class.javaObjectType)

/**
 * A database function this package has not named.
 *
 * ```kotlin
 * val cb = session.criteria
 * criteria.where(cb.function<Double>("similarity", buyer[Buyer::name], cb.literal(term)) gt 0.3)
 * ```
 *
 * The name is passed to Hibernate, which knows the ones its dialect registers and renders the rest
 * as written. The return type is the caller's claim about what comes back and nothing checks it, so
 * it is the one place here where being wrong is a runtime failure.
 */
inline fun <reified V : Any> HibernateCriteriaBuilder.function(
    name: String,
    vararg arguments: Expression<*>,
): Expression<V> = function(name, V::class.javaObjectType, *arguments)

/**
 * A fragment of SQL, dropped into the query with its arguments bound.
 *
 * ```kotlin
 * cb.sql<Boolean>("? ~ ?", buyer[Buyer::name], cb.literal("^A")) eq true
 * cb.sql<Boolean>("jsonb_exists(?, ?)", purchase[Purchase::document], cb.literal("note")) eq true
 * ```
 *
 * **`?` is a bind parameter, not a hole to interpolate into.** The trailing arguments are bound the
 * way any other parameter is, so a value carrying a quote is a value and not a second statement.
 * That is what makes this an escape hatch rather than a hazard, and it is why the fragment itself
 * should never be built by concatenating anything a caller supplied.
 *
 * This is Hibernate's own `sql()`, registered for every dialect it supports — not something this
 * module renders.
 *
 * **The fragment is rendered inline and unparenthesised**, so an operator inside it meets whatever
 * surrounds it at the database's precedence, not Kotlin's. `sql<Boolean>("? ~ ?", …) eq true` is
 * fine because `~` binds tighter than `=`; the same line with `>` in place of `~` is a Postgres
 * syntax error, because `(a > b) = c` needs the parentheses it does not get. Write the comparison
 * inside the fragment when the operator is one the surrounding expression could swallow. What the fragment may say is therefore the database's business: trigram operators,
 * `jsonb` paths, a window function HQL has no syntax for. What it cannot do is know about the
 * mapping, so column names in the fragment are the database's names and not the entity's.
 */
inline fun <reified V : Any> HibernateCriteriaBuilder.sql(
    // A fragment, not a statement: `prefix` gives it somewhere to sit so the IDE parses `? % ?`
    // as an expression instead of reporting an incomplete `select`.
    @Language("SQL", prefix = "select ", suffix = "") fragment: String,
    vararg arguments: Expression<*>,
): Expression<V> = sql(fragment.checkedAgainst(arguments.size), V::class.javaObjectType, *arguments)

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
