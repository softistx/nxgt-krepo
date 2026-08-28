package com.strange.material.form

/**
 * The rules a form needs before it needs a validation library.
 *
 * Each takes its message, and each has a default worth shipping: a form whose every field says
 * "Invalid" is a form nobody can fill in. The defaults are in English because the library has no
 * message catalogue yet — phase 10 — and a caller that has one passes it in.
 */
object Rules {
    /** Present, and not just whitespace. */
    fun required(message: String = "This field is required"): Validation<String> =
        Validation { value -> message.takeIf { value.isBlank() } }

    /** At least [min] characters. Blank passes — that is [required]'s job, and saying both is noise. */
    fun minLength(
        min: Int,
        message: String = "Use at least $min characters",
    ): Validation<String> = Validation { value -> message.takeIf { value.isNotBlank() && value.length < min } }

    /** At most [max] characters. */
    fun maxLength(
        max: Int,
        message: String = "Use at most $max characters",
    ): Validation<String> = Validation { value -> message.takeIf { value.length > max } }

    /**
     * Something that could be an email address.
     *
     * Deliberately loose. The only test that proves an address exists is sending mail to it, and a
     * strict pattern's whole yield is rejecting addresses that work.
     */
    fun email(message: String = "Enter a valid email address"): Validation<String> = pattern(EmailPattern, message)

    /** Matches [regex]. Blank passes, so an optional field with a format is one rule. */
    fun pattern(
        regex: Regex,
        message: String,
    ): Validation<String> = Validation { value -> message.takeIf { value.isNotBlank() && !regex.matches(value) } }

    /** Digits only — a PIN, a card number, a quantity typed into a text box. */
    fun digits(message: String = "Digits only"): Validation<String> =
        Validation { value -> message.takeIf { value.isNotBlank() && !value.all(Char::isDigit) } }

    /**
     * The same as whatever [other] answers *now* — a password confirmation.
     *
     * It takes a function rather than a value because the field it compares against goes on
     * changing after this rule is built.
     */
    fun matching(
        other: () -> String,
        message: String = "The two do not match",
    ): Validation<String> = Validation { value -> message.takeIf { value != other() } }

    /** Ticked. For the terms-and-conditions box, which is the only checkbox that can be wrong. */
    fun checked(message: String = "This has to be ticked"): Validation<Boolean> = Validation { value -> message.takeIf { !value } }

    /** Chosen. For a select or a radio group whose value starts as `null`. */
    fun <T> chosen(message: String = "Choose one"): Validation<T?> = Validation { value -> message.takeIf { value == null } }

    /** Not empty. For a checkbox group or a multi-select. */
    fun <T> anyOf(message: String = "Choose at least one"): Validation<Collection<T>> =
        Validation { value -> message.takeIf { value.isEmpty() } }

    /** Inside [range]. */
    fun inRange(
        range: ClosedFloatingPointRange<Float>,
        message: String = "Choose between ${range.start} and ${range.endInclusive}",
    ): Validation<Float> = Validation { value -> message.takeIf { value !in range } }
}

/** Something before an `@`, something after it, and a dot in the tail. */
private val EmailPattern = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
