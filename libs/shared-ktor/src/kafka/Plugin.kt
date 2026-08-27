package com.strange.ktor.kafka

import com.strange.kafka.Kafka
import com.strange.kafka.KafkaConfig
import com.strange.ktor.publish
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey

/**
 * The cluster configuration, in one place, reachable from a route.
 *
 * ```kotlin
 * install(KafkaCluster) { config = KafkaConfig(bootstrap = System.getenv("KAFKA_BOOTSTRAP")) }
 *
 * post("/orders") { call.kafka.publisher<OrderPlaced>().use { it.send("orders", order) } }
 * ```
 *
 * **This one opens nothing, and closes nothing.** It is the odd plugin in this module and it is odd
 * for the reason [Kafka] itself gives: a Kafka client connects when it is created, and a producer,
 * a consumer and an admin client have different lifetimes, different threads and different failure
 * modes. Pretending one object owns them all is how a wrapper ends up closing a producer something
 * else was still using. So the caller owns what it opens, exactly as it does without Ktor.
 *
 * What this is worth is what it says: one place the bootstrap and the `Json` are configured, and a
 * route that reaches for them without threading a `Kafka` through every constructor.
 *
 * A long-lived publisher or a subscriber belongs to the application rather than to a request. Open
 * it once at startup and close it on `ApplicationStopped`, the way the other plugins here do with
 * their connections.
 */
val KafkaCluster =
    createApplicationPlugin(name = "Kafka", createConfiguration = ::KafkaClusterConfiguration) {
        application.publish(KafkaKey, pluginConfig.instance ?: Kafka(pluginConfig.config))
    }

/** What [KafkaCluster] holds. */
class KafkaClusterConfiguration {
    /** Bootstrap servers, client id, the `Json` values are serialized through, and raw properties. */
    var config: KafkaConfig = KafkaConfig()

    /**
     * A cluster built elsewhere — by a DI container, or by hand. When set, [config] is ignored.
     *
     * No ownership question here, unlike the other plugins: this one has never opened anything.
     */
    var instance: Kafka? = null
}

internal val KafkaKey = AttributeKey<Kafka>("com.strange.kafka.Kafka")
