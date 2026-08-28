package com.strange.jpa.dsl

import jakarta.persistence.criteria.Expression

/**
 * `+`, so a column can be assigned from itself.
 *
 * ```kotlin
 * update<Purchase> {
 *     this[Purchase::total] set (this[Purchase::total] + 1L)
 *     where { this[Purchase::reference] eq "P-1" }
 * }
 * ```
 *
 * One statement and one round trip, and — unlike reading the value, adding to it and writing it
 * back — correct when two of them run at once.
 */
operator fun <N : Number> Expression<N>.plus(value: N): Expression<N> = builder.sum(this, value)

/** `+`, against another expression. */
operator fun <N : Number> Expression<N>.plus(other: Expression<out N>): Expression<N> = builder.sum(this, other)

/** `-`. */
operator fun <N : Number> Expression<N>.minus(value: N): Expression<N> = builder.diff(this, value)

/** `-`, against another expression. */
operator fun <N : Number> Expression<N>.minus(other: Expression<out N>): Expression<N> = builder.diff(this, other)

/** `*`. */
operator fun <N : Number> Expression<N>.times(value: N): Expression<N> = builder.prod(this, value)

/** `*`, against another expression. */
operator fun <N : Number> Expression<N>.times(other: Expression<out N>): Expression<N> = builder.prod(this, other)

/**
 * `/`, which widens.
 *
 * Division is the one operation whose result is not the operands' type — two integers divided are
 * not an integer — so this answers with `Expression<Number>` and the assignment that wants a column
 * back has to say which. That is JPA's signature, and it is right.
 */
operator fun Expression<out Number>.div(value: Number): Expression<Number> = builder.quot(this, value)

/** `/`, against another expression. */
operator fun Expression<out Number>.div(other: Expression<out Number>): Expression<Number> = builder.quot(this, other)
