package com.strange.jpa.dsl

/**
 * Marks the query DSL's scopes, so a nested one cannot reach the enclosing one by accident.
 *
 * Without it, `join(Order::customer) { … }` would resolve `this[Order::total]` against the outer
 * query scope inside the join's, silently building a path off the wrong table — the class of bug a
 * DSL exists to make impossible. With it, that is a compile error and the outer scope has to be
 * named.
 */
@DslMarker
annotation class JpaDsl
