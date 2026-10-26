@file:JvmName("KafkaSubscriberKt")

package com.softistx.kafka.consumer

import com.softistx.kafka.Kafka
import com.softistx.kafka.clientProperties
import com.softistx.kafka.serde.KafkaSerde
import com.softistx.kafka.serde.jsonSerde
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer

/**
 * A subscriber of `V` for [group], keyed by `String`, deserialized with kotlinx.serialization
 * through this cluster's `Json`.
 *
 * ```kotlin
 * kafka.subscriber<OrderEvent>("billing", listOf("orders"))
 * ```
 */
inline fun <reified V> Kafka.subscriber(
    group: String,
    topics: List<String>,
    options: SubscriberOptions = SubscriberOptions(),
    keySerde: KafkaSerde<String> = KafkaSerde.string,
    json: Json = this.json,
): KafkaSubscriber<String, V> = subscriber(group, topics, jsonSerde<V>(json), keySerde, options)

/** The same subscriber, named by its serde — for a value that is not JSON, or a `V` that cannot be reified. */
fun <K, V> Kafka.subscriber(
    group: String,
    topics: List<String>,
    valueSerde: KafkaSerde<V>,
    keySerde: KafkaSerde<K>,
    options: SubscriberOptions = SubscriberOptions(),
): KafkaSubscriber<K, V> =
    KafkaSubscriber(
        KafkaConsumer(
            config
                .clientProperties(*options.asProperties(group))
                .apply { putAll(options.properties) },
            keySerde.deserializer,
            valueSerde.deserializer,
        ),
        topics,
        options,
    )

internal fun SubscriberOptions.asProperties(group: String): Array<Pair<String, Any>> =
    buildList {
        add(ConsumerConfig.GROUP_ID_CONFIG to group)
        add(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to offsetReset.value)
        /* Never Kafka's own auto-commit: it commits on a timer whether or not the handler ran,
           which turns at-least-once into "whatever had been polled". CommitStrategy is the answer. */
        add(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false)
        maxPollRecords?.let { add(ConsumerConfig.MAX_POLL_RECORDS_CONFIG to it) }
    }.toTypedArray()
