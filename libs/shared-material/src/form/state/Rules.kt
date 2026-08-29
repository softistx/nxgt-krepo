package com.strange.material.form.state

import io.konform.validation.Constraint
import io.konform.validation.ValidationBuilder

/*
 * The rules a form reaches for, as constraints on Konform's builder.
 *
 * Each takes its message rather than owning one, because the message is the only part of a rule
 * that is not universal: the call site is where the string catalogue lives, so
 * `required(strings.fieldRequired)` reads a translated string and the library never grows a
 * catalogue of its own. The English defaults are there so a prototype needs no catalogue at all —
 * a form whose every field says "Invalid" is a form nobody can fill in.
 *
 * ```kotlin
 * override val validate = Validation {
 *     SignUpInput::email { required(); email() }
 *     SignUpInput::password { required(); minLength(8) }
 *     dynamic(SignUpInput::confirm) { form -> matching(form.password) }
 * }
 * ```
 *
 * The format rules all pass a blank value, leaving presence to [required]. That is what makes an
 * optional field with a format one rule rather than a special case, and it is why the order in a
 * block is the wording the reader gets: `required(); email()` says "This field is required" for an
 * empty box, where the reverse would say "Enter a valid email address" — true and useless.
 */

/** Present, and not just whitespace. */
fun ValidationBuilder<String>.required(message: String = "This field is required"): Constraint<String> =
    constrain(message) { it.isNotBlank() }

/**
 * Something that could be an email address.
 *
 * Deliberately loose. The only test that proves an address exists is sending mail to it, and a
 * strict pattern's whole yield is rejecting addresses that work.
 */
fun ValidationBuilder<String>.email(message: String = "Enter a valid email address"): Constraint<String> = pattern(EmailPattern, message)

/** At least [min] characters. Blank passes — that is [required]'s job, and saying both is noise. */
fun ValidationBuilder<String>.minLength(
    min: Int,
    message: String = "Use at least $min characters",
): Constraint<String> = constrain(message) { it.isBlank() || it.length >= min }

/** At most [max] characters. */
fun ValidationBuilder<String>.maxLength(
    max: Int,
    message: String = "Use at most $max characters",
): Constraint<String> = constrain(message) { it.length <= max }

/** Matches [regex]. Blank passes, so an optional field with a format is one rule. */
fun ValidationBuilder<String>.pattern(
    regex: Regex,
    message: String,
): Constraint<String> = constrain(message) { it.isBlank() || regex.matches(it) }

/** Digits only — a PIN, a card number, a quantity typed into a text box. */
fun ValidationBuilder<String>.digits(message: String = "Digits only"): Constraint<String> =
    constrain(message) { it.isBlank() || it.all(Char::isDigit) }

/**
 * The same as [other] — a password confirmation.
 *
 * The value comes from the form itself, so this belongs inside `dynamic`, which is Konform's way of
 * handing a property's rules the whole of the value they sit in:
 * `dynamic(SignUpInput::confirm) { form -> matching(form.password) }`.
 */
fun ValidationBuilder<String>.matching(
    other: String,
    message: String = "The two do not match",
): Constraint<String> = constrain(message) { it == other }

/** Ticked. For the terms-and-conditions box, which is the only checkbox that can be wrong. */
fun ValidationBuilder<Boolean>.checked(message: String = "This has to be ticked"): Constraint<Boolean> = constrain(message) { it }

/** Chosen. For a select or a radio group whose value starts as `null`. */
fun <T> ValidationBuilder<T?>.chosen(message: String = "Choose one"): Constraint<T?> = constrain(message) { it != null }

/**
 * Not empty. For a checkbox group or a multi-select.
 *
 * The receiver is the collection type itself rather than `Collection<T>` because `ValidationBuilder`
 * is invariant: a builder for `Set<String>` is not a builder for `Collection<String>`, so an
 * extension written the obvious way would not apply to the property it was written for.
 */
fun <C : Collection<*>> ValidationBuilder<C>.anyOf(message: String = "Choose at least one"): Constraint<C> =
    constrain(message) { it.isNotEmpty() }

/** Inside [range]. */
fun ValidationBuilder<Float>.inRange(
    range: ClosedFloatingPointRange<Float>,
    message: String = "Choose between ${range.start} and ${range.endInclusive}",
): Constraint<Float> = constrain(message) { it in range }

/** Something before an `@`, something after it, and a dot in the tail. */
private val EmailPattern = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
