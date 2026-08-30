package com.strange.spring.error

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What `stx.errors` configures.
 *
 * Every property this module reads is also declared in
 * `resources/META-INF/additional-spring-configuration-metadata.json`, which is what an IDE completes
 * from. It is written by hand rather than generated: `spring-boot-configuration-processor` is a
 * *Java* annotation processor and the Kotlin Toolchain has no kapt, so it never sees this class.
 * `ConfigurationMetadataTest` is what keeps the two from drifting.
 */
@ConfigurationProperties(prefix = "stx.errors")
data class ErrorProperties(
    /**
     * Registers [ApiExceptionHandler]. Off unless asked for, like every `stx.*` integration:
     * putting this library on a classpath should not change how an application already reports
     * failures.
     */
    val enabled: Boolean = false,
    /**
     * Serializes `ApiException.debugMessage` into the response.
     *
     * Off, and worth leaving off outside development. What a thrower calls a debug message is
     * routinely a query, a constraint name or an upstream response body, and a response is the one
     * place that reaches someone who was never meant to read it.
     */
    val includeDebugMessage: Boolean = false,
)
