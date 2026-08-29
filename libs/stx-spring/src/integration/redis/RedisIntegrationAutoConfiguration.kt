package com.strange.spring.integration.redis

import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import java.time.Duration
import kotlin.time.toKotlinDuration

/**
 * What `stx.redis` connects with.
 *
 * Not `spring.data.redis`, which configures Spring Data's own template over a different client.
 */
@ConfigurationProperties(prefix = "stx.redis")
data class RedisIntegrationProperties(
    /** Opens the connection. Off unless asked for. */
    val enabled: Boolean = false,
    val uri: String = "redis://localhost:6379",
    /**
     * Prefixed to every key this application writes.
     *
     * Worth setting whenever the server is shared: Redis has one flat keyspace per database, so two
     * applications with a `session:` key and no namespace are one application overwriting the other.
     */
    val namespace: String = "",
    /**
     * How long a command waits before failing.
     *
     * A `java.time.Duration` — `10s`, `PT10S` — because Spring's binder has never heard of
     * `kotlin.time.Duration`, and one written that way would bind only while nobody set it.
     */
    val timeout: Duration? = null,
)

/**
 * One `stx-redis` connection for the application.
 *
 * ```yaml
 * stx:
 *   redis: { enabled: true, uri: redis://localhost:6379, namespace: orders }
 * ```
 *
 * Built through `Redis.connect` rather than assembled here — the same rule as the rest of
 * `integration/`. Closed with the context through the inferred `close()`, which is idempotent.
 *
 * `RedisConfig.json` is not a property, because a `Json` is not a string. An application that needs
 * another one declares its own `Redis` bean; `@ConditionalOnMissingBean` steps aside.
 */
@AutoConfiguration
@EnableConfigurationProperties(RedisIntegrationProperties::class)
@ConditionalOnClass(Redis::class)
@ConditionalOnProperty(prefix = "stx.redis", name = ["enabled"], havingValue = "true")
class RedisIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun stxRedis(properties: RedisIntegrationProperties): Redis =
        Redis.connect(
            RedisConfig(uri = properties.uri, namespace = properties.namespace)
                .let { base ->
                    properties.timeout?.let { base.copy(timeout = it.toKotlinDuration()) } ?: base
                },
        )
}
