package com.softistx.i18n.spring

import com.softistx.i18n.Messages
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import org.springframework.web.server.i18n.LocaleContextResolver
import java.util.Locale

/**
 * What this auto-configuration does and, mostly, does not do.
 *
 * No backend: catalogs are files. Which of two auto-configurations ends up owning
 * `localeContextResolver` is a separate and harder question, asked in `LocaleResolverPrecedenceTest`
 * against a reactive web context.
 */
class I18nWiringTest :
    StringSpec({

        "no catalogs are loaded until an application asks" {
            runner().run { context -> context.getBeanNamesForType(Messages::class.java).size shouldBe 0 }
        }

        "enabling it loads the catalogs and narrows the locale resolver" {
            // The resolver is the half that makes this more than a Messages bean: WebFlux's default
            // answers with whatever Accept-Language asked for, catalog or no catalog.
            runner()
                .withPropertyValues("stx.i18n.enabled=true", "stx.i18n.languages=en,fr", "stx.i18n.fallback=fr")
                .run { context ->
                    context.getBean(Messages::class.java).fallback shouldBe Locale.forLanguageTag("fr")
                    context.getBean(LocaleContextResolver::class.java) shouldBe
                        context.getBean("localeContextResolver")
                }
        }

        "the languages are a property, not a line in a @Configuration class" {
            // The version this replaces hardcoded listOf("en", "fr"), so adding a language meant
            // editing the framework rather than the deployment.
            runner()
                .withPropertyValues("stx.i18n.enabled=true", "stx.i18n.languages=en,fr,de")
                .run { context ->
                    val resolver = context.getBean(AcceptHeaderLocaleContextResolver::class.java)
                    resolver.supportedLocales shouldBe listOf("en", "fr", "de").map(Locale::forLanguageTag)
                }
        }
    })

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(I18nIntegrationAutoConfiguration::class.java))
