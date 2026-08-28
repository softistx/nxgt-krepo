package com.strange.jpa.dsl

import jakarta.persistence.criteria.Predicate

/**
 * A restriction named once and used by more than one query.
 *
 * ```kotlin
 * val settled: JpaSpec<Purchase> = { Purchase::total gt 0L }
 * val ada: JpaSpec<Purchase> = { join(Purchase::customer)[Buyer::name] eq "ada" }
 *
 * session.select<Purchase>().where(settled).list()
 * session.select<Purchase>().where(settled or ada).count()
 * ```
 *
 * It is the type `where` already takes, given a name: a lambda with the query in scope, answering
 * with a predicate or with null to restrict nothing. Nothing had to be added for `where(spec)` to
 * compile — a Kotlin function type is contravariant in its receiver, so a spec written against
 * [Joins] fits a `SelectScope` and a `ProjectScope` alike.
 *
 * That makes it the same idea as `Specification<T>` in Spring Data, which is
 * `(Root<T>, CriteriaQuery<?>, CriteriaBuilder) -> Predicate` — the three arguments are the receiver
 * here, and already carry the typed vocabulary.
 *
 * **`and` is free without this; `or` is not.** Two `where` calls are already `and`ed, so a spec is
 * worth composing mostly to say `or`, which a chain cannot say. A bulk `update` or `delete` takes
 * none of these, for the reason it takes no join: see [Joins].
 */
typealias JpaSpec<T> = Joins<T>.() -> Predicate?

/**
 * Both, `and`ed — and whichever one there is when the other restricts nothing.
 *
 * Rarely needed, since `.where(a).where(b)` says the same thing. It is here so that a spec built by
 * composition reads the same either way round.
 */
infix fun <T : Any> JpaSpec<T>.and(other: JpaSpec<T>): JpaSpec<T> =
    {
        val left = this@and(this)
        val right = other(this)
        when {
            left == null -> right
            right == null -> left
            else -> builder.and(left, right)
        }
    }

/**
 * Either, `or`ed — and nothing at all when either restricts nothing.
 *
 * A spec answering null restricts nothing, which is every row; every row `or` anything is still
 * every row, so the pair adds nothing rather than quietly narrowing to the half that was there.
 */
infix fun <T : Any> JpaSpec<T>.or(other: JpaSpec<T>): JpaSpec<T> =
    {
        val left = this@or(this)
        val right = other(this)
        if (left == null || right == null) null else builder.or(left, right)
    }
