package com.strange.i18n

import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Properties

/**
 * Where a locale's messages come from.
 *
 * A seam rather than a hard-coded file read, so a spec can hand over a catalog without a file and a
 * service can load one from wherever it keeps them. Implementations answer for *one exact locale* —
 * `fr` is not asked to know about `fr-CA`, and neither is asked to know about the fallback. Walking
 * between them is [Messages]' job, and keeping it there is what makes per-key fallback possible at
 * all.
 */
fun interface MessageSource {
    /** The messages for exactly [locale], or null when this source has no catalog for it. */
    fun load(locale: Locale): Map<String, String>?
}

/**
 * Catalogs as `.properties` on the classpath, the way `ResourceBundle` lays them out:
 * `locales/messages.properties`, `locales/messages_fr.properties`, `locales/messages_fr_CA.properties`.
 *
 * **Read as strict UTF-8, which the JDK's own loader is not.** `PropertyResourceBundle` reads UTF-8
 * and silently retries the whole file as ISO-8859-1 if that fails, so a Latin-1 catalog works by
 * accident — until one is *mostly* valid UTF-8 with a stray Latin-1 byte, at which point the retry
 * does not happen and the accented characters arrive as `�`. Here a file that is not UTF-8 is a
 * [CatalogException] naming it.
 *
 * **`ResourceBundle.getBundle` is not used at all**, and that is the second reason: asked for a
 * bundle it does not have, it falls back to the *JVM default locale*. On a machine with
 * `LANG=fr_FR`, a request for Spanish quietly returns French — the sort of bug that cannot be
 * reproduced on the laptop that reported it.
 */
class PropertiesSource(
    private val baseName: String = "locales/messages",
    private val classLoader: ClassLoader = PropertiesSource::class.java.classLoader,
) : MessageSource {
    override fun load(locale: Locale): Map<String, String>? {
        val resource = resourceFor(locale)
        val stream = classLoader.getResourceAsStream(resource) ?: return null

        val decoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            stream.use { bytes ->
                InputStreamReader(bytes, decoder).use { reader ->
                    Properties()
                        .apply { load(reader) }
                        .entries
                        .associate { (key, value) -> key.toString() to value.toString() }
                }
            }
        } catch (failure: IOException) {
            throw CatalogException(resource, "is not valid UTF-8 — a properties catalog must be", failure)
        }
    }

    /** `fr_CA` becomes `messages_fr_CA.properties`; [Locale.ROOT] the unsuffixed base file. */
    private fun resourceFor(locale: Locale): String =
        when (val suffix = locale.toString()) {
            "" -> "$baseName.properties"
            else -> "${baseName}_$suffix.properties"
        }
}

/**
 * Catalogs held in memory — for a spec, and for a service whose messages are not files.
 *
 * ```kotlin
 * MapSource(Locale.ENGLISH to mapOf("hello" to "Hello {name}"))
 * ```
 */
class MapSource(
    private val catalogs: Map<Locale, Map<String, String>>,
) : MessageSource {
    constructor(vararg catalogs: Pair<Locale, Map<String, String>>) : this(catalogs.toMap())

    override fun load(locale: Locale): Map<String, String>? = catalogs[locale]
}
