package com.strange.spring.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration(proxyBeanMethods = false)
private class OwnEncoder {
    @Bean
    @Suppress("DEPRECATION")
    fun passwordEncoder(): PasswordEncoder = NoOpPasswordEncoder.getInstance()
}

class SecurityAutoConfigurationTest :
    StringSpec({
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration::class.java))

        "nothing is registered until an application asks for it" {
            // On the classpath is not the same as switched on. An application with its own security
            // setup must not find a PasswordEncoder it did not declare.
            runner.run { context ->
                context.getBeanNamesForType(PasswordEncoder::class.java).size shouldBe 0
                context.getBeanNamesForType(AnnotationTemplateExpressionDefaults::class.java).size shouldBe 0
            }
        }

        "stx.security.enabled registers the encoder and the template support" {
            runner.withPropertyValues("stx.security.enabled=true").run { context ->
                context.getBeanNamesForType(PasswordEncoder::class.java).size shouldBe 1
                // Without this bean, `@RequireRole("ADMIN")` stays the literal expression
                // `hasRole('{value}')` and denies every call — silently, which is the worst way for
                // an authorization annotation to be wrong.
                context.getBeanNamesForType(AnnotationTemplateExpressionDefaults::class.java).size shouldBe 1
            }
        }

        "the encoder is BCrypt at the configured strength" {
            runner
                .withPropertyValues("stx.security.enabled=true", "stx.security.bcrypt-strength=5")
                .run { context ->
                    // The work factor is stored in the hash itself, which is what lets it be raised
                    // later without invalidating anything already written.
                    context.getBean(PasswordEncoder::class.java).encode("secret") shouldStartWith "\$2a\$05\$"
                }
        }

        "an application's own encoder wins" {
            runner
                .withPropertyValues("stx.security.enabled=true")
                .withUserConfiguration(OwnEncoder::class.java)
                .run { context ->
                    context.getBeanNamesForType(PasswordEncoder::class.java).size shouldBe 1
                    context.getBean(PasswordEncoder::class.java).encode("secret") shouldBe "secret"
                }
        }
    })
