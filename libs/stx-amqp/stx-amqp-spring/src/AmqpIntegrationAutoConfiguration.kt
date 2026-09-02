package com.softistx.amqp.spring

import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpConfig
import kotlinx.coroutines.runBlocking
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
 * What `stx.amqp` connects to.
 *
 * **[uri] carries the virtual host, and that is the part most often got wrong.** AMQP's default
 * vhost is *named* `/`, while a URI path of `/` means the **empty** vhost — so
 * `amqp://localhost:5672/` authenticates against a vhost most brokers do not have and fails with an
 * error about permissions rather than about spelling. `amqp://localhost:5672` and
 * `amqp://localhost:5672/%2F` are the default one.
 *
 * The default names no credentials on purpose, so a deployment supplies them from its environment
 * rather than inheriting them from a source file.
 */
@ConfigurationProperties(prefix = "stx.amqp")
data class AmqpIntegrationProperties(
    /** Opens the connection at startup. Off unless asked for. */
    val enabled: Boolean = false,
    val uri: String = "amqp://localhost:5672",
    /** What this connection calls itself in the broker's management UI. Worth setting. */
    val connectionName: String? = null,
    /**
     * What makes a dead connection look dead.
     *
     * Without it a broker restart or a dropped NAT mapping leaves a socket that reads as open and
     * delivers nothing, and the client sits there. A `java.time.Duration`, which is what the binder
     * understands.
     */
    val heartbeat: Duration? = null,
    /** How long to wait for the connection itself. */
    val connectionTimeout: Duration? = null,
    /** Reconnects and re-declares after a broker restart. On by default in the library. */
    val recovery: Boolean = true,
)

/**
 * One `stx-amqp` connection for the application.
 *
 * ```yaml
 * stx:
 *   amqp: { enabled: true, uri: "amqp://user:secret@rabbit:5672/billing", connection-name: orders }
 * ```
 *
 * **`runBlocking` at bean creation**, for the same reason as `stx.jpa`: `Amqp.connect` suspends and
 * a `@Bean` method cannot. Unlike JPA, this one really does open a socket here — a broker that is
 * not there fails the startup rather than the first publish, which is the right way round for a
 * service whose work arrives over that connection.
 *
 * TLS, SASL and the recovery knobs are not properties: `AmqpConfig.configure` hands over the real
 * `ConnectionFactory`, and an application that needs it declares its own `Amqp` bean.
 */
@AutoConfiguration
@EnableConfigurationProperties(AmqpIntegrationProperties::class)
@ConditionalOnClass(Amqp::class)
@ConditionalOnProperty(prefix = "stx.amqp", name = ["enabled"], havingValue = "true")
class AmqpIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun stxAmqp(properties: AmqpIntegrationProperties): Amqp = runBlocking { Amqp.connect(properties.config()) }
}

private fun AmqpIntegrationProperties.config(): AmqpConfig {
    val defaults = AmqpConfig()
    return AmqpConfig(
        uri = uri,
        connectionName = connectionName,
        heartbeat = heartbeat?.toKotlinDuration() ?: defaults.heartbeat,
        connectionTimeout = connectionTimeout?.toKotlinDuration() ?: defaults.connectionTimeout,
        recovery = recovery,
    )
}
