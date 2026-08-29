package com.strange.spring.error

import com.strange.i18n.Messages
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Locale

/**
 * What the context does and does not get, which is the whole contract of an opt-in auto-configuration.
 *
 * `ApplicationContextRunner` builds a real context per case and never starts a server, so these run
 * in milliseconds and still exercise the conditions rather than a description of them.
 */
class ErrorAutoConfigurationTest :
    StringSpec({
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ErrorAutoConfiguration::class.java))
                .withUserConfiguration(MessagesConfiguration::class.java)

        "nothing is registered until an application asks" {
            // The rule for every stx.* integration: on the classpath is not the same as switched on.
            runner.run { context ->
                context.containsBean("apiExceptionHandler") shouldBe false
                context.getBeansOfType(ApiExceptionHandler::class.java).isEmpty() shouldBe true
            }
        }

        "stx.errors.enabled registers the handlers" {
            runner.withPropertyValues("stx.errors.enabled=true").run { context ->
                context.getBeansOfType(ApiExceptionHandler::class.java).size shouldBe 1
                // Jakarta Validation is compile-only for consumers, and is a test dependency here
                // precisely so this line means something: without it on the runtime classpath the
                // nested @ConditionalOnClass skips, which is what a consumer without validation gets.
                context.getBeansOfType(ValidationExceptionHandler::class.java).size shouldBe 1
            }
        }

        "an application's own handler wins" {
            runner
                .withPropertyValues("stx.errors.enabled=true")
                .withUserConfiguration(OwnHandlerConfiguration::class.java)
                .run { context ->
                    context.getBeansOfType(ApiExceptionHandler::class.java).keys shouldBe setOf("mine")
                }
        }

        "the debug message is off unless asked for" {
            runner.withPropertyValues("stx.errors.enabled=true").run { context ->
                context.getBean(ErrorProperties::class.java).includeDebugMessage shouldBe false
            }
            runner
                .withPropertyValues("stx.errors.enabled=true", "stx.errors.include-debug-message=true")
                .run { context ->
                    context.getBean(ErrorProperties::class.java).includeDebugMessage shouldBe true
                }
        }
    })

@Configuration(proxyBeanMethods = false)
private class MessagesConfiguration {
    @Bean
    fun messages(): Messages = Messages.of(Locale.ENGLISH to mapOf("orders.not-found" to "No such order"))
}

@Configuration(proxyBeanMethods = false)
private class OwnHandlerConfiguration {
    @Bean("mine")
    fun mine(
        messages: Messages,
        properties: ErrorProperties,
    ) = ApiExceptionHandler(messages, properties)
}
