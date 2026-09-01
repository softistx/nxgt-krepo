package com.softistx.spring.integration.i18n

import com.softistx.i18n.Messages
import com.softistx.i18n.MissingKey
import com.softistx.i18n.PropertiesSource
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Conditional
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import org.springframework.web.server.i18n.LocaleContextResolver
import java.util.Locale

/** What `stx.i18n` loads. */
@ConfigurationProperties(prefix = "stx.i18n")
data class I18nIntegrationProperties(
    /** Loads the catalogs. Off unless asked for. */
    val enabled: Boolean = false,
    /**
     * The languages with catalogs — `[en, fr]`, or `[en, fr-CA]` for a region.
     *
     * The version this replaces hardcoded `listOf("en", "fr")` in a `@Configuration` class, so
     * adding a language meant editing the framework rather than the deployment.
     */
    val languages: List<String> = listOf("en"),
    /**
     * The language a key falls back to when the request's has no text for it.
     *
     * Per key, not per request: a half-translated catalog answers in its own language where it can
     * and in this one where it cannot, rather than switching the whole response.
     */
    val fallback: String = "en",
    /** Where the `.properties` catalogs are — `locales/messages_fr.properties` for `fr`. */
    val baseName: String = "locales/messages",
    /**
     * Throw on a key with no text anywhere, instead of returning the key.
     *
     * Off in a deployment and worth turning on in a test suite: a running server should not fail a
     * request over a translation, and a test suite should not pass over one. `checkout.button` on a
     * page is ugly and diagnosable; a 500 in its place is neither.
     */
    val failOnMissingKey: Boolean = false,
) {
    internal fun locales(): List<Locale> = languages.map(Locale::forLanguageTag)

    internal fun fallbackLocale(): Locale = Locale.forLanguageTag(fallback)
}

/**
 * The application's catalogs, and a locale resolver that only answers with a language it has.
 *
 * ```yaml
 * stx:
 *   i18n: { enabled: true, languages: [ en, fr ], fallback: en }
 * ```
 *
 * ```kotlin
 * messages.forRequest(exchange)["orders.title"]
 * ```
 *
 * **The resolver is the second half, and the reason this is more than a `Messages` bean.** WebFlux's
 * default answers with whatever `Accept-Language` asked for, catalog or no catalog, so a browser
 * asking for Japanese produces a `Translator` for Japanese that falls back key by key. Told the
 * supported set, it answers with the closest language actually loaded, and `stx.i18n.fallback` is
 * what a request naming none gets.
 *
 * **Registered before `WebFluxAutoConfiguration`, and that ordering is load-bearing.** Boot's
 * resolver is `@ConditionalOnMissingBean(name = "localeContextResolver")` and this one is a plain
 * `@ConditionalOnMissingBean`, so the two collide on a bean name and whichever is processed first
 * wins. Without the ordering Boot's won *every time* — measured, including with no `spring.web.*`
 * property set at all — leaving `stx.i18n.languages` and `stx.i18n.fallback` with no effect on
 * anything. `LocaleResolverPrecedenceTest` is the spec that says so.
 *
 * **Boot's own properties still win when an application sets them.** [BootLocaleUnset] stands this
 * resolver down when `spring.web.locale` or `spring.web.locale-resolver` is present: a `stx.*` key
 * is a better default than WebFlux's, never an override of what the application asked for by name.
 *
 * No `@ConditionalOnClass` here, unlike the rest of `integration/`: `stx-i18n` is an `exported`
 * dependency of this module rather than a compile-only one — the exception handler translates — so
 * the class is always present and the condition would only ever be true.
 */
@AutoConfiguration(before = [WebFluxAutoConfiguration::class])
@EnableConfigurationProperties(I18nIntegrationProperties::class)
@ConditionalOnProperty(prefix = "stx.i18n", name = ["enabled"], havingValue = "true")
class I18nIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun stxMessages(properties: I18nIntegrationProperties): Messages =
        Messages.load(
            source = PropertiesSource(properties.baseName),
            locales = properties.locales(),
            fallback = properties.fallbackLocale(),
            missingKey = if (properties.failOnMissingKey) MissingKey.Fail else MissingKey.ReturnKey,
        )

    @Bean
    @ConditionalOnMissingBean
    @Conditional(BootLocaleUnset::class)
    fun localeContextResolver(properties: I18nIntegrationProperties): LocaleContextResolver =
        AcceptHeaderLocaleContextResolver().apply {
            supportedLocales = properties.locales()
            defaultLocale = properties.fallbackLocale()
        }
}
