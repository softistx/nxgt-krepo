package com.strange.material.form

/**
 * A rule, as a function from a value to the reason it is wrong.
 *
 * `null` means the value passes. Everything else is the message a person reads, which is why rules
 * return a `String` rather than a boolean: a rule that knows it failed also knows *why*, and asking
 * the call site to supply the wording again is how a form ends up saying "Invalid" four times.
 *
 * This library deliberately depends on no validation library. A rule is a function, so plugging one
 * in is an adapter and not a migration:
 *
 * ```kotlin
 * fun konform(validation: Validation<String>) = Validation<String> { value ->
 *     validation.validate(value).errors.firstOrNull()?.message
 * }
 * ```
 */
fun interface Validation<in T> {
    /** The reason [value] is unacceptable, or `null` if it is fine. */
    fun check(value: T): String?
}

/**
 * Both rules, in order, and the first complaint wins.
 *
 * Order is the message the reader gets: `required() and email()` says "This field is required" for
 * an empty box, where the reverse would say "Enter a valid email address" — technically true and
 * useless.
 */
infix fun <T> Validation<T>.and(other: Validation<T>): Validation<T> = Validation { value -> check(value) ?: other.check(value) }

/** Every rule in the list, in order. An empty list accepts everything. */
fun <T> List<Validation<T>>.all(): Validation<T> = Validation { value -> firstNotNullOfOrNull { it.check(value) } }
