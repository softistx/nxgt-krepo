package com.strange.i18n

import java.util.Locale

/** What this module throws that the JDK and ICU do not. */
sealed class I18nException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * A catalog that could not be read.
 *
 * Overwhelmingly this is an encoding failure. A `.properties` file that is not UTF-8 is read here
 * as the mistake it is, rather than decoded as Latin-1 and turned into text that looks almost
 * right — `L'attribut à mettre à jour` arriving as `L'attribut � mettre � jour` is a bug that
 * reaches production because nobody on the team reads the language it broke.
 */
class CatalogException(
    val resource: String,
    message: String,
    cause: Throwable? = null,
) : I18nException("$resource: $message", cause)

/**
 * A key no catalog has, under [MissingKey.Fail].
 *
 * The locales are the ones that were asked, in order, so the message says where it looked rather
 * than only what it wanted.
 */
class MissingMessageException(
    val key: String,
    val locales: List<Locale>,
) : I18nException("no message for '$key' in ${locales.joinToString(", ") { it.toLanguageTag() }}")

/**
 * A message whose pattern ICU cannot parse, or whose arguments do not fit it.
 *
 * Raised at *load* for a pattern that will never work — a stray `{`, an unclosed plural — so a
 * broken translation is a failed startup rather than a failed screen. Under [MissingKey.Fail] it is
 * also raised for arguments that do not fit a pattern that is otherwise fine.
 */
class MalformedMessageException(
    val key: String,
    val locale: Locale,
    message: String,
    cause: Throwable? = null,
) : I18nException("'$key' in ${locale.toLanguageTag()}: $message", cause)
