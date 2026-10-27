package com.softistx.kafka.consumer

import kotlinx.coroutines.CompletableDeferred
import org.apache.kafka.common.TopicPartition

/** What a handler or a collector asks of the poll loop, in the order it asked. */
internal sealed interface LoopMessage {
    /** This record has been handled; its offset may be committed once the prefix below it is. */
    data class Completed(
        val partition: TopicPartition,
        val offset: Long,
        val epoch: Long,
    ) : LoopMessage

    /** Commit what is committable and say so. */
    data class CommitNow(
        val done: CompletableDeferred<Unit>,
    ) : LoopMessage
}
