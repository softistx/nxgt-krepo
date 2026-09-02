package com.softistx.i18n.spring

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import org.springframework.web.server.i18n.FixedLocaleContextResolver
import org.springframework.web.server.i18n.LocaleContextResolver
import java.util.Locale

/**
 * Who owns `localeContextResolver` when both `stx.i18n` and Spring Boot have an opinion.
 *
 * There is exactly one bean of that name in a context, and two auto-configurations that want to be
 * it: Boot's is `@ConditionalOnMissingBean(name = "localeContextResolver")` and this module's is a
 * plain `@ConditionalOnMissingBean`, so whichever is processed first wins and the other backs off.
 * That makes the outcome a property of *ordering*, which is exactly the kind of thing that is
 * plausible either way until it is measured.
 *
 * It was measured, and it was wrong: before `@AutoConfiguration(before = WebFluxAutoConfiguration)`
 * Boot's resolver won in every arrangement — including with no `spring.web.*` property set at all,
 * and regardless of which order the two were handed to the runner. `stx.i18n.languages` and
 * `stx.i18n.fallback` configured a bean that never reached a request. Nothing failed; the feature
 * was simply absent, and the reference page described what it was meant to do.
 *
 * The rule these three scenarios encode: **a `stx.*` key is a better default than the framework's,
 * never an override of what the application asked for by name.**
 */
private fun runner() =
    ReactiveWebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                WebFluxAutoConfiguration::class.java,
                I18nIntegrationAutoConfiguration::class.java,
            ),
        ).withPropertyValues(
            "stx.i18n.enabled=true",
            "stx.i18n.languages=en,fr",
            "stx.i18n.fallback=fr",
        )

/** An application that declares its own. Nothing here may displace it. */
@Configuration(proxyBeanMethods = false)
private class OwnResolver {
    @Bean
    fun localeContextResolver(): LocaleContextResolver = FixedLocaleContextResolver(Locale.ITALIAN)
}

class LocaleResolverPrecedenceTest :
    StringSpec({

        "stx.i18n narrows the resolver to the languages it actually loaded" {
            runner().run { context ->
                val resolver = context.getBean(LocaleContextResolver::class.java)
                // Boot's is an AcceptHeaderLocaleContextResolver too, so the type alone proves
                // nothing — the supported set is what distinguishes them.
                (resolver as AcceptHeaderLocaleContextResolver).supportedLocales shouldBe
                    listOf(Locale.ENGLISH, Locale.FRENCH)
                resolver.defaultLocale shouldBe Locale.FRENCH
            }
        }

        "spring.web.locale-resolver wins, because the application asked for it by name" {
            runner()
                .withPropertyValues("spring.web.locale=de", "spring.web.locale-resolver=fixed")
                .run { context ->
                    context.getBean(LocaleContextResolver::class.java)::class.java shouldBe
                        FixedLocaleContextResolver::class.java
                }
        }

        "spring.web.locale alone is enough to stand this module's resolver down" {
            runner().withPropertyValues("spring.web.locale=de").run { context ->
                val resolver = context.getBean(LocaleContextResolver::class.java) as AcceptHeaderLocaleContextResolver
                // Boot's, configured from spring.web.locale — not this module's [en, fr]/fr.
                resolver.supportedLocales shouldBe emptyList()
                resolver.defaultLocale shouldBe Locale.GERMAN
            }
        }

        "an application's own bean beats both of us" {
            runner().withUserConfiguration(OwnResolver::class.java).run { context ->
                context.getBeanNamesForType(LocaleContextResolver::class.java).size shouldBe 1
                context.getBean(LocaleContextResolver::class.java)::class.java shouldBe
                    FixedLocaleContextResolver::class.java
            }
        }

        "the catalogs load either way — only the resolver stands down" {
            runner().withPropertyValues("spring.web.locale=de").run { context ->
                context.getBeanNamesForType(com.softistx.i18n.Messages::class.java).size shouldBe 1
            }
        }
    })
