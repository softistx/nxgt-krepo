package com.softistx.i18n

import java.util.Locale

/**
 * The catalogs, bound to one locale.
 *
 * ```kotlin
 * val fr = messages.forLocale(Locale.FRENCH)
 *
 * fr["orders.title"]
 * fr.translate("hello.world", mapOf("name" to "Ada"))
 * fr.translate("items.count", mapOf("count" to 3))     // {count, plural, one {…} other {…}}
 * ```
 *
 * **Prefer named arguments to positional ones.** `{name}` survives a translator rewriting the
 * sentence in another word order; `{0}` survives it only by luck, and ICU's plural and select forms
 * — the reason to use ICU at all — read as nonsense with numbers for names.
 */
class Translator internal constructor(
    val locale: Locale,
    private val messages: Messages,
) {
    /** The message for [key], with no arguments. */
    operator fun get(key: String): String = translate(key)

    /** The message for [key], formatted with positional arguments — `{0}`, `{1}`. */
    fun translate(
        key: String,
        vararg args: Any,
    ): String =
        when (val message = resolve(key)) {
            null -> missing(key)
            is Message.Literal -> message.text
            is Message.Pattern -> format(key, message) { message.format(args) }
        }

    /** The message for [key], formatted with named arguments — `{name}`, `{count}`. */
    fun translate(
        key: String,
        args: Map<String, Any>,
    ): String =
        when (val message = resolve(key)) {
            null -> {
                missing(key)
            }

            is Message.Literal -> {
                message.text
            }

            is Message.Pattern -> {
                /* ICU leaves the placeholder standing for an argument nobody passed, which is what
                   a server should do and what a test must not accept. */
                val absent = message.argumentNames - args.keys
                if (absent.isNotEmpty() && messages.missingKey == MissingKey.Fail) {
                    throw MalformedMessageException(key, locale, "needs ${absent.sorted().joinToString(", ")}")
                }
                format(key, message) { message.format(args) }
            }
        }

    /** Whether any catalog along this locale's chain answers [key]. */
    operator fun contains(key: String): Boolean = resolve(key) != null

    private fun resolve(key: String): Message? = messages.resolve(key, locale)

    private fun missing(key: String): String =
        when (messages.missingKey) {
            MissingKey.ReturnKey -> key
            MissingKey.Fail -> throw MissingMessageException(key, (locale.chain() + messages.fallback.chain()).distinct())
        }

    /**
     * Formatting failures follow the same policy as missing keys: a page showing its own pattern is
     * bad and a request failing over it is worse, unless this is a test, where the opposite holds.
     */
    private fun format(
        key: String,
        message: Message.Pattern,
        format: () -> String,
    ): String =
        try {
            format()
        } catch (failure: IllegalArgumentException) {
            when (messages.missingKey) {
                MissingKey.ReturnKey -> message.text
                MissingKey.Fail -> throw MalformedMessageException(key, locale, "arguments do not fit the pattern", failure)
            }
        }
}
