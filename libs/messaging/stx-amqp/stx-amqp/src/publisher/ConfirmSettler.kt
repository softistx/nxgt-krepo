package com.softistx.amqp.publisher

import com.softistx.common.coroutines.Mailbox

/**
 * Applies what the broker says, in the order it says it.
 *
 * Sole owner of both maps below, which is why neither is synchronised and why [Pending.returned]
 * is an ordinary `var`.
 */
internal suspend fun Mailbox<Confirm>.settle() {
    /* Insertion order is ascending sequence order, so "everything up to this tag" is a walk
       from the front rather than a sorted structure. */
    val pending = LinkedHashMap<Long, Pending>()
    val bySequence = mutableMapOf<String, Long>()
    var lost = 0L

    fun forget(entry: Pending) {
        pending.remove(entry.sequence)
        entry.messageId?.let(bySequence::remove)
    }

    fun take(
        tag: Long,
        multiple: Boolean,
    ): List<Pending> =
        when {
            multiple -> pending.keys.takeWhile { it <= tag }.mapNotNull { pending[it] }
            else -> listOfNotNull(pending[tag])
        }.onEach(::forget)

    try {
        consume { message ->
            when (message) {
                is Confirm.Registered -> {
                    if (message.epoch < lost) {
                        /* Its sequence number was read before the channel was lost, so the
                           loss has already been applied and nothing will ever confirm it. */
                        message.entry.abandoned()
                    } else {
                        pending[message.entry.sequence] = message.entry
                        message.entry.messageId?.let { bySequence[it] = message.entry.sequence }
                    }
                }

                /* The broker sends a return before the confirm for that message, so marking it
                   here and failing it on the confirm needs no second signal. */
                is Confirm.Returned -> {
                    bySequence[message.messageId]?.let { pending[it]?.returned = message.replyText }
                }

                is Confirm.Acked -> {
                    take(message.tag, message.multiple).forEach { it.confirmed() }
                }

                is Confirm.Nacked -> {
                    take(message.tag, message.multiple).forEach { it.nacked() }
                }

                is Confirm.Failed -> {
                    take(message.sequence, false).forEach { it.failed(message.cause) }
                }

                is Confirm.Lost -> {
                    lost = message.epoch
                    pending.values.forEach { it.abandoned() }
                    pending.clear()
                    bySequence.clear()
                }
            }
        }
    } finally {
        pending.values.forEach { it.abandoned() }
    }
}
