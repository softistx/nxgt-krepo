package com.softistx.kafka.consumer

import com.softistx.kafka.record.KafkaRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.apache.kafka.common.TopicPartition

/**
 * [Concurrency.PerPartition]: one worker per partition, so a partition's records stay in order while
 * other partitions run alongside it, with at most `concurrency.limit` handlers at once.
 * [handled] is called after each handler returned.
 */
internal suspend fun <K, V> Flow<KafkaRecord<K, V>>.processPerPartition(
    concurrency: Concurrency.PerPartition,
    handler: suspend (KafkaRecord<K, V>) -> Unit,
    handled: (KafkaRecord<K, V>) -> Unit,
) = coroutineScope {
    val partitions = mutableMapOf<TopicPartition, SendChannel<KafkaRecord<K, V>>>()
    val inFlight = Semaphore(concurrency.limit)

    try {
        collect { record ->
            val partition = TopicPartition(record.topic, record.partition)
            partitions
                .getOrPut(partition) {
                    Channel<KafkaRecord<K, V>>(Channel.BUFFERED).also { queue ->
                        launch {
                            for (queued in queue) {
                                inFlight.withPermit { handler(queued) }
                                handled(queued)
                            }
                        }
                    }
                }.send(record)
        }
    } finally {
        /* The workers end when their channel does: without this a closed subscriber leaves
           them waiting and the scope never returns. They finish what is already queued first. */
        partitions.values.forEach { it.close() }
    }
}
