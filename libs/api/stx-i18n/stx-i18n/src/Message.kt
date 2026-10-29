package com.softistx.i18n

import com.ibm.icu.text.MessageFormat
import com.softistx.common.concurrent.Guarded
import java.util.Locale

/**
 * One message, in the form it will be used.
 *
 * Split on whether it takes arguments at all, because the overwhelming majority do not — of the 55
 * messages in the project this module was written for, one had a placeholder. A literal needs no
 * formatter, no lock and no allocation to return, and separating the two here is what keeps that
 * true at the call site.
 */
internal sealed interface Message {
    val text: String

    /** No placeholders: the answer is the text, whatever arguments were offered. */
    data class Literal(
        override val text: String,
    ) : Message

    /**
     * Has placeholders, so it holds a compiled ICU pattern.
     *
     * Compiled once — parsing a pattern costs far more than formatting with it — and held in a
     * [Guarded], because ICU documents `MessageFormat` as unsynchronized and asks callers to do
     * exactly this. `Guarded` rather than a `synchronized` block on the field so that there is no
     * way to reach the format without the lock: the rule stops being one a future reader has to
     * notice. The lock is uncontended in the normal case and only ever taken for a message that
     * actually has arguments.
     */
    class Pattern(
        override val text: String,
        val key: String,
        val locale: Locale,
    ) : Message {
        private val format =
            Guarded(
                try {
                    MessageFormat(text, locale)
                } catch (failure: IllegalArgumentException) {
                    throw MalformedMessageException(key, locale, "is not a valid ICU message pattern", failure)
                },
            )

        /**
         * What this pattern needs supplied, read off it once.
         *
         * ICU is lenient about an argument nobody passed — it leaves `{count}` in the output rather
         * than complaining — which is the right default for a running server and useless to a test.
         * Knowing the names is what lets [MissingKey.Fail] tell the difference.
         */
        val argumentNames: Set<String> = format.withLock { it.argumentNames }

        fun format(arguments: Array<out Any>): String = format.withLock { it.format(arguments) }

        fun format(arguments: Map<String, Any>): String = format.withLock { it.format(arguments) }
    }

    companion object {
        fun of(
            key: String,
            locale: Locale,
            text: String,
        ): Message = if ('{' in text) Pattern(text, key, locale) else Literal(text)
    }
}
