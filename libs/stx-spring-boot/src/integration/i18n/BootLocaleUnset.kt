package com.softistx.spring.integration.i18n

import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/** The Spring Boot keys that say the application has an opinion of its own about locale resolution. */
private val BOOT_LOCALE_KEYS = listOf("spring.web.locale", "spring.web.locale-resolver")

/**
 * Matches when the application has set neither `spring.web.locale` nor `spring.web.locale-resolver`.
 *
 * **Where a `stx.*` key overlaps one of Spring Boot's, Boot's is in charge.** `stx.i18n` narrows the
 * locale resolver to the languages it actually loaded catalogs for, which is a better default than
 * WebFlux's — that one answers with whatever `Accept-Language` asked for — but it is still only a
 * default. An application that wrote `spring.web.locale` down meant it, and a library that silently
 * ignored the framework's own property in favour of its own would be a library nobody could reason
 * about from a `application.yaml`.
 *
 * A condition rather than `@ConditionalOnProperty`, because there is no "this property is absent"
 * form of that annotation and two keys to check. It also reads correctly in the condition evaluation
 * report, which is where somebody will look when the resolver is not the one they expected.
 */
internal class BootLocaleUnset : SpringBootCondition() {
    override fun getMatchOutcome(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): ConditionOutcome {
        val declared = BOOT_LOCALE_KEYS.filter { context.environment.containsProperty(it) }
        return if (declared.isEmpty()) {
            ConditionOutcome.match("no Spring Boot locale property is set")
        } else {
            ConditionOutcome.noMatch("${declared.joinToString()} is set, so Spring Boot's resolver stays in charge")
        }
    }
}
