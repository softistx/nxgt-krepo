package com.softistx.amqp.consumer

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ShutdownSignalException

/** What a consumer's flow does when its channel reports a shutdown. */
internal enum class ShutdownOutcome { Continue, End, Fail }

/**
 * Decides what a shutdown means for a flow.
 *
 * Only a *connection-level* loss that the client will recover from leaves the flow open — the
 * consumer is re-registered on the new channel with the same callbacks. A close this side asked
 * for ends it quietly; a channel closed by the broker, or any loss without recovery, ends it with
 * the signal as the reason.
 */
internal fun shutdownOutcome(
    signal: ShutdownSignalException,
    recoverable: Boolean,
): ShutdownOutcome =
    when {
        signal.isInitiatedByApplication -> ShutdownOutcome.End
        recoverable && signal.reference is Connection -> ShutdownOutcome.Continue
        else -> ShutdownOutcome.Fail
    }
