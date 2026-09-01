package com.softistx.telemetry.export

import com.softistx.common.coroutines.Mailbox
import com.softistx.common.lifecycle.CloseGuard
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Signal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * The queue between the code that writes a signal and the exporters that ship it.
 *
 * **Writing a log must never make a caller wait, and must never make it fail.** That is the whole
 * requirement, and it decides everything here: the way in is `stx-common`'s [Mailbox], whose `post`
 * is a `trySend` on an unbounded channel — it always succeeds, never suspends, and can be called
 * from any thread, including one that could not suspend if it wanted to. A bounded queue would
 * answer the back-pressure question by dropping signals or by blocking the application, and neither
 * is an answer.
 *
 * There is exactly **one consumer**, which is what lets [buffer] be a plain `ArrayList` with no
 * synchronisation and no `@Volatile` anywhere — and what makes a batch's order the order the signals
 * were written in. `AmqpPublisher` uses the same shape for the same reason.
 *
 * Batching is by size *or* by time, and the timer is a [Flush] message posted into the same mailbox
 * rather than a second coroutine touching the buffer. A second coroutine would need a lock, which
 * would undo the paragraph above to save nothing.
 */
internal class Pipeline(
    private val resource: Resource,
    private val exporters: List<Exporter>,
    private val batch: Int,
    private val linger: Duration,
    private val drainTimeout: Duration,
    private val onError: (Throwable) -> Unit,
) : AutoCloseable {
    private val mailbox = Mailbox<Any>()
    private val guard = CloseGuard()

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("stx-telemetry"))

    private val ticker =
        scope.launch {
            while (true) {
                delay(linger)
                mailbox.post(Flush)
            }
        }

    private val drain = scope.launch { consume() }

    /** Never blocks, never throws, safe from any thread. False only once this is closed. */
    fun post(signal: Signal): Boolean = mailbox.post(signal)

    private suspend fun consume() {
        val buffer = ArrayList<Signal>(batch)
        mailbox.consume { message ->
            when (message) {
                is Signal -> {
                    buffer += message
                    if (buffer.size >= batch) flush(buffer)
                }

                else -> {
                    flush(buffer)
                }
            }
        }
        flush(buffer)
    }

    private suspend fun flush(buffer: MutableList<Signal>) {
        if (buffer.isEmpty()) return
        val outgoing = buffer.toList()
        buffer.clear()
        for (exporter in exporters) {
            try {
                exporter.export(resource, outgoing)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                report(failure)
            }
        }
    }

    private fun report(failure: Throwable) {
        try {
            onError(failure)
        } catch (_: Throwable) {
            // A failure handler that fails is not allowed to take the only consumer down with it.
        }
    }

    /**
     * Stops accepting signals, ships what is already queued, and closes the exporters.
     *
     * It **blocks**, and it has to: a `close` is called from a shutdown hook, a `use` block or a
     * container's teardown, none of which can suspend, and a close that returned before the backlog
     * was shipped would lose exactly the signals a shutdown most needs to explain itself.
     *
     * [drainTimeout] bounds that: a destination that has stopped answering must not become the
     * reason a process will not exit.
     */
    override fun close() =
        guard.once {
            ticker.cancel()
            mailbox.close()
            runBlocking { withTimeoutOrNull(drainTimeout) { drain.join() } }
            scope.cancel()
            for (exporter in exporters) {
                try {
                    exporter.close()
                } catch (failure: Throwable) {
                    report(failure)
                }
            }
        }

    /** The timer's message. Nothing about it reaches an exporter; it only ends the current linger. */
    private object Flush
}
