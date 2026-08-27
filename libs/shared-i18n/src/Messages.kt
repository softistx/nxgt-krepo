package com.strange.i18n

import com.ibm.icu.text.MessageFormat
import java.util.Locale

/**
 * Every catalog this application ships, loaded once and never changed after.
 *
 * ```kotlin
 * val messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH))
 *
 * messages.forLocale(Locale.FRENCH).translate("hello.world", mapOf("name" to "Ada"))
 * ```
 *
 * **Loading is eager, and that is the point.** Every pattern is parsed here, so a `{` somebody left
 * open is a failed startup rather than a failed screen three weeks later in the one language nobody
 * on the team reads. The catalogs are immutable afterwards, so nothing below needs a lock, and the
 * locales it holds are the ones the caller asked for — a library with a hard-coded list of
 * languages is a library you have to edit to ship a new one.
 *
 * **Lookup walks, per key.** For a message in `fr-CA`: `fr-CA`, then `fr`, then the [fallback]
 * locale and its own chain, then the unsuffixed base catalog, then [MissingKey]. Walking per key
 * rather than choosing one catalog up front is what lets `fr-CA` be a handful of overrides — and is
 * the difference between a French user seeing English for a key nobody has translated yet and
 * seeing `checkout.button`.
 */
class Messages internal constructor(
    private val catalogs: Map<Locale, Map<String, Message>>,
    private val root: Map<String, Message>,
    val fallback: Locale,
    internal val missingKey: MissingKey,
) {
    /**
     * The locales this can serve.
     *
     * The [fallback] is always among them even when it has no suffixed catalog of its own — a
     * project that keeps its English in the unsuffixed `messages.properties` still serves English,
     * and this set is what a negotiation is matched against.
     */
    val locales: Set<Locale> get() = catalogs.keys + fallback

    /** A view bound to one locale. Cheap: it holds a locale and this. */
    fun forLocale(locale: Locale): Translator = Translator(locale, this)

    /** Every key any catalog answers, for an audit or a completeness check. */
    fun keys(): Set<String> = catalogs.values.flatMapTo(mutableSetOf()) { it.keys } + root.keys

    /**
     * A view for the best catalog an `Accept-Language` header asks for.
     *
     * ```kotlin
     * messages.negotiate(call.request.headers[HttpHeaders.AcceptLanguage])
     * ```
     */
    fun negotiate(acceptLanguage: String?): Translator = forLocale(negotiate(acceptLanguage, locales, fallback))

    /**
     * Each catalog measured against the fallback's: what it cannot answer, and what only it has.
     *
     * A locale is measured through its own chain, so `fr-CA` overriding two keys is complete as
     * long as `fr` is — otherwise every regional overlay would report itself as almost entirely
     * missing, and an audit nobody can act on is an audit nobody runs.
     */
    fun audit(): CatalogAudit {
        val reference = catalogs[fallback]?.keys.orEmpty() + root.keys

        return CatalogAudit(
            fallback = fallback,
            reference = reference,
            locales =
                catalogs.keys.filter { it != fallback }.map { locale ->
                    val answerable = locale.chain().mapNotNull { catalogs[it] }.flatMapTo(mutableSetOf()) { it.keys }
                    LocaleAudit(
                        locale = locale,
                        missing = reference - answerable,
                        /* Its own keys only: an extra in `fr` is `fr`'s to fix, and reporting it
                           again under every region over it is noise. */
                        extra = catalogs[locale].orEmpty().keys - reference,
                    )
                },
        )
    }

    /**
     * The message for [key] in the first catalog along [locale]'s chain that has one.
     *
     * Internal because a caller wants a [Translator]: this is the walk, not the API.
     */
    internal fun resolve(
        key: String,
        locale: Locale,
    ): Message? {
        searchOrder(locale).forEach { catalog -> catalog[key]?.let { return it } }
        return null
    }

    internal fun searchOrder(locale: Locale): List<Map<String, Message>> =
        buildList {
            (locale.chain() + fallback.chain()).distinct().forEach { step -> catalogs[step]?.let(::add) }
            add(root)
        }

    companion object {
        /**
         * Loads [locales] from [source], compiling every pattern.
         *
         * The unsuffixed catalog — `locales/messages.properties` — is loaded too and sits underneath
         * all of them, which is how a project that keeps its English there and its French in
         * `messages_fr.properties` works without being rearranged.
         */
        fun load(
            source: MessageSource = PropertiesSource(),
            locales: List<Locale> = listOf(Locale.ENGLISH),
            fallback: Locale = Locale.ENGLISH,
            missingKey: MissingKey = MissingKey.ReturnKey,
        ): Messages {
            val catalogs =
                (locales + fallback)
                    .distinct()
                    .mapNotNull { locale ->
                        source.load(locale)?.let { locale to it.compile(locale) }
                    }.toMap()

            return Messages(
                catalogs = catalogs,
                /* The unsuffixed catalog holds the fallback language's text, so it compiles
                   with that locale — not Locale.ROOT, whose plural rules have only an `other`
                   form and would quietly render "1 orders". */
                root = source.load(Locale.ROOT).orEmpty().compile(fallback),
                fallback = fallback,
                missingKey = missingKey,
            )
        }

        /** The same, from catalogs in hand — the shape a spec wants. */
        fun of(
            vararg catalogs: Pair<Locale, Map<String, String>>,
            fallback: Locale = Locale.ENGLISH,
            missingKey: MissingKey = MissingKey.ReturnKey,
        ): Messages =
            load(
                source = MapSource(*catalogs),
                locales = catalogs.map { it.first },
                fallback = fallback,
                missingKey = missingKey,
            )

        private fun Map<String, String>.compile(locale: Locale): Map<String, Message> =
            mapValues { (key, text) -> Message.of(key, locale, text) }
    }
}

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
     * Compiled once — parsing a pattern costs far more than formatting with it — and formatted
     * under a lock, because ICU documents `MessageFormat` as unsynchronized and asks callers to do
     * exactly this. The lock is uncontended in the normal case and is only ever taken for a message
     * that actually has arguments.
     */
    class Pattern(
        override val text: String,
        val key: String,
        val locale: Locale,
    ) : Message {
        private val format =
            try {
                MessageFormat(text, locale)
            } catch (failure: IllegalArgumentException) {
                throw MalformedMessageException(key, locale, "is not a valid ICU message pattern", failure)
            }

        /**
         * What this pattern needs supplied, read off it once.
         *
         * ICU is lenient about an argument nobody passed — it leaves `{count}` in the output rather
         * than complaining — which is the right default for a running server and useless to a test.
         * Knowing the names is what lets [MissingKey.Fail] tell the difference.
         */
        val argumentNames: Set<String> = format.argumentNames

        fun format(arguments: Array<out Any>): String = synchronized(format) { format.format(arguments) }

        fun format(arguments: Map<String, Any>): String = synchronized(format) { format.format(arguments) }
    }

    companion object {
        fun of(
            key: String,
            locale: Locale,
            text: String,
        ): Message = if ('{' in text) Pattern(text, key, locale) else Literal(text)
    }
}
