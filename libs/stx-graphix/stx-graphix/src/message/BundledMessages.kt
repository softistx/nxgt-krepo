package com.softistx.graphix.message

import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** Where a catalogue lives on the classpath: `stx/graphix/messages.properties`, then `_fr`, `_fr_CA`. */
private const val CATALOG = "stx/graphix/messages"

/**
 * The catalogues in this jar. English is the base, French ships beside it, and the lookup walks
 * `fr-CA` → `fr` → base **per key**, so a partial catalogue is a partial catalogue and not a
 * partial language.
 *
 * A locale this jar does not ship is a `stx/graphix/messages_<locale>.properties` on the application's
 * own classpath — the loader reads the path, not this artifact. Anything more than that (ICU
 * plurals, a database, a translation service) is a [GraphixMessages] of your own.
 *
 * Catalogues are read as UTF-8 **strictly**: a file that is not UTF-8 fails loudly rather than
 * arriving as mojibake, which is `stx-i18n`'s rule and for the same reason.
 */
internal object BundledMessages : GraphixMessages {
    private val catalogs = ConcurrentHashMap<String, Map<String, String>>()
    private val placeholder = Regex("""\{([A-Za-z0-9_]+)}""")

    override fun message(
        locale: Locale,
        key: String,
        args: Map<String, Any>,
    ): String {
        val text = suffixes(locale).firstNotNullOfOrNull { catalog(it)[key] } ?: return key
        return interpolate(text, args)
    }

    /** `fr-CA` reads `_fr_CA`, then `_fr`, then the unsuffixed base. */
    private fun suffixes(locale: Locale): List<String> =
        listOf("_$locale", "_${locale.language}", "")
            .filter { it != "_" }
            .distinct()

    private fun catalog(suffix: String): Map<String, String> = catalogs.getOrPut(suffix) { load(suffix) }

    private fun load(suffix: String): Map<String, String> {
        val stream = javaClass.classLoader.getResourceAsStream("$CATALOG$suffix.properties") ?: return emptyMap()
        val decoder =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val properties = Properties()
        stream.use { properties.load(InputStreamReader(it, decoder)) }
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }

    /** `{name}` from [args]. A placeholder nobody supplied is left standing, not blanked. */
    private fun interpolate(
        text: String,
        args: Map<String, Any>,
    ): String {
        if (args.isEmpty() || '{' !in text) return text
        return placeholder.replace(text) { match ->
            args[match.groupValues[1]]?.toString() ?: match.value
        }
    }
}
