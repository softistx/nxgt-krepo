package com.softistx.i18n

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
