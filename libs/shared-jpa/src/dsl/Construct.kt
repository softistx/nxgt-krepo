package com.strange.jpa.dsl

import jakarta.persistence.criteria.Selection

/**
 * A row built by calling a constructor, with the arguments typed by the constructor itself.
 *
 * ```kotlin
 * class Summary(val reference: String, val name: String)
 *
 * session.project<Purchase, Summary> {
 *     val buyer = join(Purchase::customer)
 *     construct(::Summary, this[Purchase::reference], buyer[Buyer::name])
 * }.list()
 * ```
 *
 * **The constructor reference is not used at runtime; it is there to type the arguments.** Criteria
 * takes a `Class` and a list of selections and checks the match when the query is built, which is
 * late. Naming the constructor instead makes the compiler check it: a `Long` column where the
 * constructor wants a `String`, or two arguments the right types in the wrong order, is a compile
 * error naming the constructor that did not fit.
 *
 * Hibernate still calls the constructor reflectively, so it has to be public and its parameters have
 * to be in this order — which is exactly what the reference asserts.
 */
fun <T : Any, R : Any, A> ProjectScope<T, R>.construct(
    ctor: (A) -> R,
    a: Selection<A>,
): Selection<R> {
    ignore(ctor)
    return builder.construct(resultType, a)
}

/** The same, for a constructor of 2 arguments. */
fun <T : Any, R : Any, A, B> ProjectScope<T, R>.construct(
    ctor: (A, B) -> R,
    a: Selection<A>,
    b: Selection<B>,
): Selection<R> {
    ignore(ctor)
    return builder.construct(resultType, a, b)
}

/** The same, for a constructor of 3 arguments. */
fun <T : Any, R : Any, A, B, C> ProjectScope<T, R>.construct(
    ctor: (A, B, C) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: Selection<C>,
): Selection<R> {
    ignore(ctor)
    return builder.construct(resultType, a, b, c)
}

/** The same, for a constructor of 4 arguments. */
fun <T : Any, R : Any, A, B, C, D> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: Selection<C>,
    d: Selection<D>,
): Selection<R> {
    ignore(ctor)
    return builder.construct(resultType, a, b, c, d)
}

/** The same, for a constructor of 5 arguments. */
fun <T : Any, R : Any, A, B, C, D, E> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D, E) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: Selection<C>,
    d: Selection<D>,
    e: Selection<E>,
): Selection<R> {
    ignore(ctor)
    return builder.construct(resultType, a, b, c, d, e)
}

/** The same, for a constructor of 6 arguments. */
fun <T : Any, R : Any, A, B, C, D, E, F> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D, E, F) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: Selection<C>,
    d: Selection<D>,
    e: Selection<E>,
    f: Selection<F>,
): Selection<R> {
    ignore(ctor)
    return builder.construct(resultType, a, b, c, d, e, f)
}

/** The same, for a constructor of 7 arguments. */
fun <T : Any, R : Any, A, B, C, D, E, F, G> ProjectScope<T, R>.construct(
    ctor: (A, B, C, D, E, F, G) -> R,
    a: Selection<A>,
    b: Selection<B>,
    c: Selection<C>,
    d: Selection<D>,
    e: Selection<E>,
    f: Selection<F>,
    g: Selection<G>,
): Selection<R> {
    ignore(ctor)
    return builder.construct(resultType, a, b, c, d, e, f, g)
}

/**
 * Swallows the constructor reference.
 *
 * It has done its work by the time this runs — the compiler has already matched the selections
 * against the constructor's parameters. Saying so here rather than leaving an unused parameter that
 * reads like an oversight.
 */
private fun ignore(ctor: Function<*>) = check(ctor !== null)
