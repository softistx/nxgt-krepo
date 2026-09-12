package com.softistx.jpa.criteria

import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

/**
 * A restriction with a name, so more than one query can ask for it.
 *
 * ```kotlin
 * val available: JpaSpec<Product> = { it[Product::discontinued] eq false }
 * fun named(term: String): JpaSpec<Product> = { it[Product::name] ilike "%$term%" }
 *
 * products.findAll(session, available and named("anvil"))
 * ```
 *
 * It is a plain function type over Criteria's own [Root] — nothing was added to make it one, and
 * there is no interface to implement. That is the same idea as Spring Data's `Specification<T>`,
 * which is `(Root<T>, CriteriaQuery<?>, CriteriaBuilder) -> Predicate`; the other two arguments are
 * unnecessary here because `Root` carries the builder (`Expression.builder`) and a spec has no
 * business reaching the query.
 *
 * **Answering `null` restricts nothing**, which is what a filter that turned out not to apply should
 * mean. A repository call given a null spec queries the whole table, deliberately: that is `findAll`.
 */
typealias JpaSpec<T> = (Root<T>) -> Predicate?

/**
 * Both, or whichever of them restricts anything.
 *
 * Two specs that both answer `null` answer `null` together, so composing filters a request may or
 * may not have asked for needs no branching at the call site.
 */
infix fun <T : Any> JpaSpec<T>.and(other: JpaSpec<T>): JpaSpec<T> = { root -> combine(this(root), other(root)) { a, b -> a and b } }

/**
 * Either.
 *
 * The one that earns its keep: a repository `and`s a spec onto whatever else it was given, and no
 * chain of calls can say `or`. Note that `or` with a spec that restricts nothing is still every row,
 * not the half the other side would have kept — which is what `null` meaning *no restriction* has to
 * imply, and the reason to keep an optional filter out of an `or`.
 */
infix fun <T : Any> JpaSpec<T>.or(other: JpaSpec<T>): JpaSpec<T> = { root -> combine(this(root), other(root)) { a, b -> a or b } }

/** Every one of them, `and`ed — for a list of filters built up before the query was. */
fun <T : Any> all(specs: List<JpaSpec<T>>): JpaSpec<T> =
    { root -> specs.fold<JpaSpec<T>, Predicate?>(null) { acc, spec -> combine(acc, spec(root)) { a, b -> a and b } } }

private fun combine(
    left: Predicate?,
    right: Predicate?,
    join: (Predicate, Predicate) -> Predicate,
): Predicate? =
    when {
        left == null -> right
        right == null -> left
        else -> join(left, right)
    }
