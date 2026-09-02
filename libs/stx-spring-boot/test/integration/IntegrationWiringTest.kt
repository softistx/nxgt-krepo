package com.softistx.spring.integration

import com.softistx.i18n.Messages
import com.softistx.spring.integration.i18n.I18nIntegrationAutoConfiguration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import org.springframework.web.server.i18n.LocaleContextResolver
import java.util.Locale

/**
 * What the `integration/` auto-configurations do and, mostly, do not do.
 *
 * Every one of them is the same shape — `@ConditionalOnClass`, `@ConditionalOnProperty` with no
 * `matchIfMissing`, `@ConditionalOnMissingBean` on every bean, built through that library's own
 * factory — so the specs are the same three questions asked of each: nothing without the property,
 * the beans with it, and an application's own bean winning.
 *
 * None of these connect to anything. Every client here is lazy about its first connection, which is
 * what lets a wiring spec assert the wiring without a server — and is also why a wrong password
 * surfaces on first use rather than at startup.
 */
class IntegrationWiringTest :
    StringSpec({

        "i18n: no catalogs are loaded until an application asks" {
            i18n().run { context -> context.getBeanNamesForType(Messages::class.java).size shouldBe 0 }
        }

        "i18n: enabling it loads the catalogs and narrows the locale resolver" {
            // The resolver is the half that makes this more than a Messages bean: WebFlux's default
            // answers with whatever Accept-Language asked for, catalog or no catalog.
            i18n()
                .withPropertyValues("stx.i18n.enabled=true", "stx.i18n.languages=en,fr", "stx.i18n.fallback=fr")
                .run { context ->
                    context.getBean(Messages::class.java).fallback shouldBe Locale.forLanguageTag("fr")
                    context.getBean(LocaleContextResolver::class.java) shouldBe
                        context.getBean("localeContextResolver")
                }
        }

        "i18n: the languages are a property, not a line in a @Configuration class" {
            // The version this replaces hardcoded listOf("en", "fr"), so adding a language meant
            // editing the framework rather than the deployment.
            i18n()
                .withPropertyValues("stx.i18n.enabled=true", "stx.i18n.languages=en,fr,de")
                .run { context ->
                    val resolver = context.getBean(AcceptHeaderLocaleContextResolver::class.java)
                    resolver.supportedLocales shouldBe listOf("en", "fr", "de").map(Locale::forLanguageTag)
                }
        }
    })

private fun i18n() = runnerFor(I18nIntegrationAutoConfiguration::class.java)

private fun runnerFor(type: Class<*>) = ApplicationContextRunner().withConfiguration(AutoConfigurations.of(type))
