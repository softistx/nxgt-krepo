package com.strange.kafka.serde

import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer

/**
 * The two halves of one type's wire form, together.
 *
 * Kafka keeps them apart — a `Serializer` for producing, a `Deserializer` for consuming — and
 * configures them by class name in a property map, which means a serializer that needs an argument
 * has nowhere to put it. Passing instances to the client constructors is the supported way out, and
 * carrying the pair as one value is what lets a publisher and a subscriber be handed the same
 * agreement about a type rather than two halves that might disagree.
 *
 * [json] is what the typed factories give you. The others are for keys and for the values that are
 * not JSON because something else produces them.
 */
data class KafkaSerde<T>(
    val serializer: Serializer<T>,
    val deserializer: Deserializer<T>,
) {
    companion object {
        val string: KafkaSerde<String> = of(Serdes.String())

        val long: KafkaSerde<Long> = of(Serdes.Long())

        val int: KafkaSerde<Int> = of(Serdes.Integer())

        val bytes: KafkaSerde<ByteArray> = of(Serdes.ByteArray())

        /** Kafka's own `Serde` as this pair — the bridge for anything already written against it. */
        fun <T> of(serde: org.apache.kafka.common.serialization.Serde<T>): KafkaSerde<T> =
            KafkaSerde(serde.serializer(), serde.deserializer())
    }
}
