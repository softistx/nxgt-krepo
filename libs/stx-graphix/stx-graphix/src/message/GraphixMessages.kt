package com.softistx.graphix.message

import java.util.Locale

/**
 * Where an error's text comes from. A coercion error is the one Graphix message that reaches an
 * API **client** rather than a developer — a schema that cannot be built throws at startup, in one
 * language, at whoever wrote it — so it is the one that is looked up by key and locale.
 *
 * The default is [Bundled]: the catalogues shipped in this jar, English and French. An
 * application with a catalogue of its own supplies one lambda, and `stx-i18n` is a
 * [Translator][com.softistx.i18n.Translator] away:
 *
 * ```kotlin
 * Graphix {
 *     messages { locale, key, args -> catalog.forLocale(locale).translate(key, args) }
 *     resolvers(ProductQueries(store))
 * }
 * ```
 *
 * That indirection is the whole reason this interface exists rather than a dependency: ICU4J is a
 * 15 MB jar, and `stx-i18n`'s own README is why it is not in `stx-common`. A service that never
 * translates anything should not carry a message formatter to run GraphQL.
 *
 * The locale is the operation's — [GraphixRequest.locale][com.softistx.graphix.GraphixRequest],
 * which the HTTP integrations negotiate from `Accept-Language`. [MessageKeys] is the key list.
 *
 * A source declared this way answers for a **variable's** value and for a resolver's result. A
 * **literal in the document** is coerced by graphql-java's validator, which builds its own context
 * carrying only the locale, and is answered by [Bundled] — in the requested language, because the
 * locale does reach it. Adding a language rather than replacing the source has no such split: see
 * [BundledMessages] for the classpath the catalogues are read from.
 */
fun interface GraphixMessages {
    /**
     * The text for [key] in [locale], with [args] interpolated. A key this source does not know
     * must still produce something printable — the key itself is the conventional answer, and is
     * what [Bundled] does.
     */
    fun message(
        locale: Locale,
        key: String,
        args: Map<String, Any>,
    ): String

    companion object {
        /** The catalogues in this jar. See [MessageKeys] for what a replacement has to answer. */
        val Bundled: GraphixMessages get() = BundledMessages
    }
}
