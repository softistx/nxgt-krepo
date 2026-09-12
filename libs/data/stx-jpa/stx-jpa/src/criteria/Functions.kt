package com.softistx.jpa.criteria

import jakarta.persistence.criteria.Expression

/**
 * The functions HQL has a word for, with the builder taken off the argument.
 *
 * ```kotlin
 * criteria.where(lower(buyer[Buyer::name]) eq term.lowercase())
 * ```
 *
 * They are ordinary top-level functions rather than members of anything, so they nest and compose
 * the way they read — `length(trim(buyer[Buyer::name]))` — and a caller who needs one that is not
 * here writes it the same way, or reaches for [function] and names it.
 */
fun lower(value: Expression<String>): Expression<String> = value.builder.lower(value)

/** `upper`. */
fun upper(value: Expression<String>): Expression<String> = value.builder.upper(value)

/** `trim`, both ends. */
fun trim(value: Expression<String>): Expression<String> = value.builder.trim(value)

/** How many characters, which is not how many bytes. */
fun length(value: Expression<String>): Expression<Int> = value.builder.length(value)

/** `substring`, one-based like SQL and unlike Kotlin. */
fun substring(
    value: Expression<String>,
    start: Int,
    length: Int? = null,
): Expression<String> = if (length == null) value.builder.substring(value, start) else value.builder.substring(value, start, length)

/** `concat`. */
fun concat(
    first: Expression<String>,
    second: Expression<String>,
): Expression<String> = first.builder.concat(first, second)

/** `concat`, with a literal on the right. */
fun concat(
    first: Expression<String>,
    second: String,
): Expression<String> = first.builder.concat(first, second)

/** `abs`. */
fun <N : Number> abs(value: Expression<N>): Expression<N> = value.builder.abs(value)

/** `sqrt`, which is a double whatever went in. */
fun sqrt(value: Expression<out Number>): Expression<Double> = value.builder.sqrt(value)

/** `mod`. */
fun mod(
    value: Expression<Int>,
    divisor: Int,
): Expression<Int> = value.builder.mod(value, divisor)

/**
 * The first of the two that is not null.
 *
 * The usual reason to reach for it is a sort or a comparison over a nullable column, where the
 * database's idea of where nulls belong is not the caller's.
 */
fun <V> coalesce(
    value: Expression<out V>,
    fallback: Expression<out V>,
): Expression<V> = value.builder.coalesce(value, fallback)

/** The same, with a literal fallback. */
fun <V> coalesce(
    value: Expression<out V>,
    fallback: V,
): Expression<V> = value.builder.coalesce(value, fallback)

/** Null when the two are equal, the value otherwise — the inverse of [coalesce]. */
fun <V> nullIf(
    value: Expression<V>,
    other: V,
): Expression<V> = value.builder.nullif(value, other)
