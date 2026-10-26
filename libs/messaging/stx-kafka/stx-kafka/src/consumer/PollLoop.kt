package com.softistx.kafka.consumer

import com.softistx.kafka.record.KafkaRecord
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * The poll loop: the one place that calls the client, on the subscriber's dispatcher.
 *
 * It never stops polling — when the buffer to the handler fills it pauses its partitions and keeps
 * calling `poll` — and it commits on revocation, before partitions are handed over.
 */
internal class PollLoop<K, V>(
    private val consumer: Consumer<K, V>,
    private val topics: List<String>,
    private val options: SubscriberOptions,
    private val ownership: Ownership,
    private val commits: LoopCommits<K, V>,
    private val polling: MutableStateFlow<Boolean>,
    private val assigned: MutableStateFlow<Set<TopicPartition>>,
) {
    /** Records polled and not yet handed to the buffer — touched only by the loop and its rebalance listener. */
    private val waiting = ArrayDeque<KafkaRecord<K, V>>()

    suspend fun pollInto(buffer: Channel<OwnedRecord<K, V>>) {
        waiting.clear()
        polling.value = true
        consumer.subscribe(topics, RevocationCommit())
        try {
            while (currentCoroutineContext().isActive) {
                commits.drainMailbox()

                while (waiting.isNotEmpty()) {
                    val owned = ownership.stamp(waiting.first())
                    if (owned != null && !buffer.trySend(owned).isSuccess) break
                    waiting.removeFirst()
                }

                // Full buffer: stop being given records, but go on proving we are alive.
                if (waiting.isNotEmpty()) {
                    consumer.pause(consumer.assignment())
                } else {
                    val resumable = consumer.paused() intersect consumer.assignment()
                    if (resumable.isNotEmpty()) consumer.resume(resumable)
                }

                val due = commits.commitDue()
                val timeout =
                    when {
                        due -> Duration.ZERO
                        waiting.isNotEmpty() -> PAUSED_POLL
                        else -> options.pollTimeout
                    }
                consumer.poll(timeout.toJavaDuration()).forEach { waiting.addLast(KafkaRecord.from(it)) }
                assigned.value = consumer.assignment().toSet()

                if (due) commits.commitAndAnswer()
            }
        } catch (_: WakeupException) {
            // close() asked for this.
        } finally {
            /* Reported stopped before the last drain, so a commit that arrives after this either
               lands in the drain below or finds polling false and serves itself. */
            polling.value = false
            // Ends the `for (record in buffer)` of records(), so a closed subscriber ends its flow.
            buffer.close()
            waiting.clear()
            commits.drainMailbox()
            runCatching { commits.commitNow() }
            commits.answer { it.complete(Unit) } // Nobody else will.
        }
    }

    /**
     * Kafka calls this inside `poll`, on the loop's own turn, and waits for it — which is exactly
     * the window in which a partition's last offsets can still be committed by the consumer that
     * owns it. Draining first is what includes the records handled during the poll being rebalanced.
     */
    private inner class RevocationCommit : ConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
            commits.drainMailbox()
            runCatching { commits.commitAndAnswer() }
            commits.forget(partitions)
            drop(partitions)
        }

        override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
            ownership.assigned(partitions)
        }

        override fun onPartitionsLost(partitions: Collection<TopicPartition>) {
            /* Lost, not revoked: the partitions are already someone else's, and committing their
               offsets now would be claiming work this consumer no longer owns. */
            commits.forget(partitions)
            drop(partitions)
        }
    }

    /**
     * Takes [partitions] out of everything not yet handled: the records waiting for room are
     * removed, and the ones already in the buffer stop being current, so `records()` skips them.
     */
    private fun drop(partitions: Collection<TopicPartition>) {
        ownership.revoked(partitions)
        val gone = partitions.toSet()
        waiting.removeAll { TopicPartition(it.topic, it.partition) in gone }
    }

    private companion object {
        /** How long a paused poll waits — long enough not to spin, short enough to notice room. */
        val PAUSED_POLL = 100.milliseconds
    }
}
