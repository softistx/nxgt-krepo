package com.strange.kafka.producer

import com.strange.kafka.Kafka
import com.strange.kafka.clientProperties
import com.strange.kafka.record.RecordHeaders
import com.strange.kafka.serde.KafkaSerde
import com.strange.kafka.serde.jsonSerde
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Instant

/**
 * Records out, one suspending call at a time.
 *
 * ```kotlin
 * kafka.publisher<OrderEvent>().use { orders ->
 *     orders.send("orders", OrderPlaced(id), key = id)
 * }
 * ```
 *
 * [send] suspends until the broker has acknowledged the record on the terms
 * [PublisherOptions.acks] asked for, and answers with where it landed. Kafka's own `send` returns a
 * `Future` and takes a callback; awaiting the callback is what turns "fired" into "stored", and a
 * caller that does not want to wait can still pipeline by launching sends concurrently — the
 * producer batches them regardless.
 *
 * **`send` can block before it ever reaches the callback.** The client parks the calling thread
 * while it fetches metadata for an unknown topic, and while the accumulator is full, for up to
 * `max.block.ms`. That is why the call runs on [Dispatchers.IO]: on a coroutine dispatcher those
 * are threads the rest of the application needed.
 *
 * One publisher is the right number per configuration. `KafkaProducer` is thread-safe, batches
 * across callers, and holds connections to the whole cluster; two of them halve the batching and
 * double the sockets.
 */
class KafkaPublisher<K, V> internal constructor(
    internal val producer: Producer<K, V>,
) : AutoCloseable {
    /**
     * Sends one record and waits for the acknowledgement.
     *
     * [key] is what decides the partition, and therefore the order: records sharing a key land on
     * one partition and are read in the order they were written. A null key spreads records across
     * partitions and gives up that ordering — which is the right trade for an event nobody has to
     * see in sequence, and the wrong one for a per-entity change feed.
     */
    suspend fun send(
        topic: String,
        value: V?,
        key: K? = null,
        headers: RecordHeaders = RecordHeaders.EMPTY,
        partition: Int? = null,
        timestamp: Instant? = null,
    ): SentRecord =
        send(
            ProducerRecord(
                topic,
                partition,
                timestamp?.toEpochMilliseconds(),
                key,
                value,
                headers.toKafka(),
            ),
        )

    suspend fun send(record: ProducerRecord<K, V>): SentRecord =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                producer.send(record) { metadata, failure ->
                    if (failure != null) {
                        continuation.resumeWithException(failure)
                    } else {
                        continuation.resume(SentRecord.from(metadata))
                    }
                }
            }
        }

    /**
     * Sends every record and waits for all of them.
     *
     * The records are handed over **in order** and the acknowledgements awaited together. Both
     * halves matter: the order they enter the producer's accumulator is the order they are written
     * to a partition, so enqueuing them from concurrent threads would quietly shuffle records that
     * share a key — and awaiting them one at a time would turn one batched round trip into one per
     * record.
     *
     * Cancelling the caller stops the waiting, not the sending: a record already handed to the
     * producer is on its way, and Kafka has no way to recall it.
     */
    suspend fun sendAll(records: Collection<ProducerRecord<K, V>>): List<SentRecord> =
        withContext(Dispatchers.IO) {
            records
                .map { record ->
                    CompletableDeferred<SentRecord>().also { pending ->
                        producer.send(record) { metadata, failure ->
                            if (failure != null) {
                                pending.completeExceptionally(failure)
                            } else {
                                pending.complete(SentRecord.from(metadata))
                            }
                        }
                    }
                }.awaitAll()
        }

    /** Waits for everything already handed over to be acknowledged. */
    suspend fun flush() = withContext(Dispatchers.IO) { producer.flush() }

    override fun close() = producer.close()
}

/**
 * A publisher of `V`, keyed by `String`, serialized with kotlinx.serialization through this
 * cluster's `Json`.
 *
 * ```kotlin
 * val orders = kafka.publisher<OrderEvent>()
 * ```
 */
inline fun <reified V> Kafka.publisher(
    options: PublisherOptions = PublisherOptions(),
    keySerde: KafkaSerde<String> = KafkaSerde.string,
    json: Json = this.json,
): KafkaPublisher<String, V> = publisher(jsonSerde<V>(json), options, keySerde)

/** The same publisher, named by its serde — for a value that is not JSON, or a `V` that cannot be reified. */
fun <K, V> Kafka.publisher(
    valueSerde: KafkaSerde<V>,
    options: PublisherOptions = PublisherOptions(),
    keySerde: KafkaSerde<K>,
): KafkaPublisher<K, V> =
    KafkaPublisher(
        KafkaProducer(
            config.clientProperties(*options.asProperties()).apply { putAll(options.properties) },
            keySerde.serializer,
            valueSerde.serializer,
        ),
    )

internal fun PublisherOptions.asProperties(): Array<Pair<String, Any>> =
    buildList {
        add(ProducerConfig.ACKS_CONFIG to acks.value)
        add(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to idempotent)
        add(ProducerConfig.LINGER_MS_CONFIG to linger.inWholeMilliseconds.toInt())
        add(ProducerConfig.MAX_BLOCK_MS_CONFIG to maxBlock.inWholeMilliseconds.toInt())
        add(ProducerConfig.COMPRESSION_TYPE_CONFIG to compression.value)
    }.toTypedArray()
