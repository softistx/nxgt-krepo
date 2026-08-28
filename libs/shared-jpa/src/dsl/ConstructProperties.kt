package com.strange.jpa.dsl

import jakarta.persistence.criteria.Selection
import kotlin.reflect.KProperty1

/**
 * [construct], with a column named by the entity's own property instead of by a path.
 *
 * ```kotlin
 * construct(::Summary, Purchase::reference, buyer[Buyer::name])
 * ```
 *
 * A property reference is not a `Selection` and Kotlin has no implicit conversion, so a position
 * that accepts both has to be two declarations — which is why there is one of these for every
 * mixture of properties and selections up to four columns, and a property-only one above that.
 * Generated shape rather than written prose: each delegates to the all-selection overload in
 * `Construct.kt` after turning its properties into paths, and nothing else happens here.
 *
 * A property only names a column of the entity being selected. Anything else — a joined column, a
 * function, an aggregate — is a selection, which is what the mixture is for.
 *
 * **Above four columns, write every column as a path**: `this[Purchase::reference]` rather than
 * `Purchase::reference`. A fifth column mixing the two forms is an overload-resolution error listing
 * every candidate here, and this is the sentence that answers it. `ProjectDslTest` pins the escape.
 *
 * The cliff is arithmetic rather than an omission, and it is why this file stops where it does: a
 * mixture of two forms over n columns is 2ⁿ − 1 declarations, so four columns cost 15 and reaching
 * seven the same way would cost 221 more. Four is where the sugar still pays — it covers the
 * projections people actually write — and the uniform path form covers everything, at every arity,
 * with one rule and no overload set to resolve against.
 */
fun <T : Any, R : Any, A> ProjectScope<T, R>.construct(
    ctor: (A) -> R,
    a: KProperty1<T, A>,
): Selection<R> = construct(ctor, this[a])

fun <T : Any, R : Any, A, B> ProjectScope<T, R>.construct(
    ctor: (A, B) -> R,
    a: Selection<A>,
    b: KProperty1<T, B>,
): Selection<R> = construct(ctor, a, this[b])

fun <T : Any, R : Any, A, B> ProjectScope<T, R>.construct(
    ctor: (A, B) -> R,
    a: KProperty1<T, A>,
    b: Selection<B>,
): Selection<R> = construct(ctor, this[a], b)

fun <T : Any, R : Any, A, B> ProjectScope<T, R>.construct(
    ctor: (A, B) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
): Selection<R> = construct(ctor, this[a], this[b])

fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: KProperty1<T, C>,
): Selection<R> = construct(ctor, a, b, this[c])

fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: Selection<A>,
    b: KProperty1<T, B>,
    c: Selection<C>,
): Selection<R> = construct(ctor, a, this[b], c)

fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: Selection<A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
): Selection<R> = construct(ctor, a, this[b], this[c])

fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: KProperty1<T, A>,
    b: Selection<B>,
    c: Selection<C>,
): Selection<R> = construct(ctor, this[a], b, c)

fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: KProperty1<T, A>,
    b: Selection<B>,
    c: KProperty1<T, C>,
): Selection<R> = construct(ctor, this[a], b, this[c])

fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: Selection<C>,
): Selection<R> = construct(ctor, this[a], this[b], c)

fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
): Selection<R> = construct(ctor, this[a], this[b], this[c])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: Selection<C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, a, b, c, this[d])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: KProperty1<T, C>,
    d: Selection<D>,
): Selection<R> = construct(ctor, a, b, this[c], d)

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: KProperty1<T, C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, a, b, this[c], this[d])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: KProperty1<T, B>,
    c: Selection<C>,
    d: Selection<D>,
): Selection<R> = construct(ctor, a, this[b], c, d)

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: KProperty1<T, B>,
    c: Selection<C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, a, this[b], c, this[d])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
    d: Selection<D>,
): Selection<R> = construct(ctor, a, this[b], this[c], d)

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, a, this[b], this[c], this[d])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: Selection<B>,
    c: Selection<C>,
    d: Selection<D>,
): Selection<R> = construct(ctor, this[a], b, c, d)

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: Selection<B>,
    c: Selection<C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, this[a], b, c, this[d])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: Selection<B>,
    c: KProperty1<T, C>,
    d: Selection<D>,
): Selection<R> = construct(ctor, this[a], b, this[c], d)

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: Selection<B>,
    c: KProperty1<T, C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, this[a], b, this[c], this[d])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: Selection<C>,
    d: Selection<D>,
): Selection<R> = construct(ctor, this[a], this[b], c, d)

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: Selection<C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, this[a], this[b], c, this[d])

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
    d: Selection<D>,
): Selection<R> = construct(ctor, this[a], this[b], this[c], d)

fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
    d: KProperty1<T, D>,
): Selection<R> = construct(ctor, this[a], this[b], this[c], this[d])

fun <T : Any, R : Any, A, B, C, D, E> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D, E) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
    d: KProperty1<T, D>,
    e: KProperty1<T, E>,
): Selection<R> = construct(ctor, this[a], this[b], this[c], this[d], this[e])

fun <T : Any, R : Any, A, B, C, D, E, F> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D, E, F) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
    d: KProperty1<T, D>,
    e: KProperty1<T, E>,
    f: KProperty1<T, F>,
): Selection<R> = construct(ctor, this[a], this[b], this[c], this[d], this[e], this[f])

fun <T : Any, R : Any, A, B, C, D, E, F, G> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D, E, F, G) -> R,
    a: KProperty1<T, A>,
    b: KProperty1<T, B>,
    c: KProperty1<T, C>,
    d: KProperty1<T, D>,
    e: KProperty1<T, E>,
    f: KProperty1<T, F>,
    g: KProperty1<T, G>,
): Selection<R> = construct(ctor, this[a], this[b], this[c], this[d], this[e], this[f], this[g])
