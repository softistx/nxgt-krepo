package com.strange.jpa.criteria

import jakarta.persistence.criteria.Expression

/**
 * `+`, so a column can be assigned from itself.
 *
 * ```kotlin
 * val statement = session.createUpdate<Purchase>()
 * val purchase = statement.from(Purchase::class.java)
 * statement.set<Long>(purchase[Purchase::total], purchase[Purchase::total] + 1L)
 * statement.where(purchase[Purchase::reference] eq "P-1")
 *
 * session.mutate(statement).execute()
 * ```
 *
 * One statement and one round trip, and — unlike reading the value, adding to it and writing it
 * back — correct when two of them run at once.
 *
 * The type argument on `set` is not optional: JPA declares both `set(Path<Y>, X)` and
 * `set(Path<Y>, Expression<out Y>)`, and against an expression Kotlin finds them equally applicable.
 * Naming `Y` picks the second.
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
