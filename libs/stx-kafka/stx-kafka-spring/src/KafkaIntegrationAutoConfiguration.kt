package com.softistx.kafka.spring

import com.softistx.kafka.Kafka
import com.softistx.kafka.KafkaConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/** What `stx.kafka` talks to. */
@ConfigurationProperties(prefix = "stx.kafka")
data class KafkaIntegrationProperties(
    /** Registers the cluster handle. Off unless asked for. */
    val enabled: Boolean = false,
    /** `host:port` pairs, comma-separated. */
    val bootstrap: String = "localhost:9092",
    /**
     * What this application calls itself to the broker.
     *
     * Worth setting: it is what a broker's metrics and logs name when they name a client, and
     * `consumer-1` in an incident is not a name.
     */
    val clientId: String? = null,
    /** Anything else the clients understand, applied last. */
    val properties: Map<String, String> = emptyMap(),
)

/**
 * The application's `stx-kafka` cluster handle.
 *
 * ```yaml
 * stx:
 *   kafka: { enabled: true, bootstrap: "localhost:9092", client-id: orders }
 * ```
 *
 * **This bean opens nothing, and has no `close()` to call.** `Kafka` is deliberately not a
 * `connect()`: a Kafka client connects when it is constructed, so the connections belong to the
 * publishers, subscribers and admin clients it hands out — each with its own lifetime, thread and
 * failure mode, and each closed by whoever asked for it. A wrapper that owned them all would
 * eventually close a producer another part of the application was still using.
 *
 * So this is the one integration where the context is not holding a connection, and the one where
 * an application still has real work to do: `kafka.publisher<OrderEvent>()` is a resource, and it
 * is yours.
 */
@AutoConfiguration
@EnableConfigurationProperties(KafkaIntegrationProperties::class)
@ConditionalOnClass(Kafka::class)
@ConditionalOnProperty(prefix = "stx.kafka", name = ["enabled"], havingValue = "true")
class KafkaIntegrationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun stxKafka(properties: KafkaIntegrationProperties): Kafka =
        Kafka(
            KafkaConfig(
                bootstrap = properties.bootstrap,
                clientId = properties.clientId,
                properties = properties.properties,
            ),
        )
}
