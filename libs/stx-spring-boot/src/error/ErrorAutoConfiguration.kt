package com.softistx.spring.error

import com.softistx.i18n.Messages
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the error handling when `stx.errors.enabled` is true.
 *
 * **Opt-in, with no `matchIfMissing`** — the rule for every `stx.*` integration in this module.
 * Putting this library on a classpath must not change how an application already reports failures;
 * a starter that takes over error handling the moment it is present is the kind of surprise that
 * gets a library removed.
 *
 * [ApiExceptionHandler] needs a [Messages] bean. An application either declares one or adds
 * `com.softistx:stx-i18n-spring` and turns on `stx.i18n`, which contributes it — this module
 * exports [Messages] the *type*, not the auto-configuration that builds one. There is deliberately
 * no fallback that skips translation: a response body reading `orders.not-found` in production is
 * worse than a context that refuses to start and says which bean is missing.
 */
@AutoConfiguration
@EnableConfigurationProperties(ErrorProperties::class)
@ConditionalOnProperty(prefix = "stx.errors", name = ["enabled"], havingValue = "true")
class ErrorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun apiExceptionHandler(
        messages: Messages,
        properties: ErrorProperties,
    ) = ApiExceptionHandler(messages, properties)

    /**
     * Nested so the `@ConditionalOnClass` applies to this bean alone.
     *
     * On the outer class it would gate the whole configuration, and an application without Jakarta
     * Validation — a legitimate combination, since it is `compile-only` here — would silently get no
     * error handling at all rather than the part that does not need it.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["jakarta.validation.ConstraintViolationException"])
    class ValidationErrorConfiguration {
        @Bean
        @ConditionalOnMissingBean
        fun validationExceptionHandler(properties: ErrorProperties) = ValidationExceptionHandler(properties)
    }
}
