package com.strange.telemetry.context

import com.strange.telemetry.Attributes
import com.strange.telemetry.Telemetry
import com.strange.telemetry.attributesOf
import com.strange.telemetry.trace.SpanContext
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Where this library keeps "what is happening right now".
 *
 * It is a [CoroutineContext.Element], and that is the whole design. A request in a coroutine service
 * is not a thread: it suspends on a database call and resumes on whichever worker is free, so
 * anything kept in a `ThreadLocal` and never updated on that hop belongs to whoever ran there last.
 * This repository has written that argument three times already — `LocaleContextHolder` serving
 * French to English users under load, an auditor stamping documents with the previous request's
 * user — and SLF4J's MDC is the same `ThreadLocal`. An observability library built on it would
 * reproduce, in the very tool meant to make such bugs visible, the bug this repository refuses to
 * have.
 *
 * ## Then why is there a thread-local here at all
 *
 * Because `log.info(…)` must not be a suspending function. A log written from an `init` block, a
 * `catch` in ordinary blocking code, or a Java callback is a log that still has to come out, and a
 * suspending logger cannot be called from any of them.
 *
 * So this class is *also* a [ThreadContextElement]: the coroutine machinery calls
 * [updateThreadContext] on every dispatch and [restoreThreadContext] on every suspension, which
 * keeps a plain thread-local in step with the coroutine context automatically. **The coroutine
 * context is the source of truth and the thread-local is a mirror the runtime maintains.** That is
 * the precise difference from an MDC, which is a thread-local nobody updates when the work moves —
 * and the reason the two look alike and behave nothing alike.
 */
class TelemetryContext internal constructor(
    val telemetry: Telemetry,
    val span: SpanContext?,
    val attributes: Attributes,
) : AbstractCoroutineContextElement(Key),
    ThreadContextElement<TelemetryContext?> {
    internal fun with(
        span: SpanContext? = this.span,
        attributes: Attributes = this.attributes,
    ) = TelemetryContext(telemetry, span, attributes)

    override fun updateThreadContext(context: CoroutineContext): TelemetryContext? {
        val previous = mirror.get()
        mirror.set(this)
        return previous
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: TelemetryContext?,
    ) {
        if (oldState == null) mirror.remove() else mirror.set(oldState)
    }

    companion object Key : CoroutineContext.Key<TelemetryContext> {
        /**
         * The mirror, and nothing reads it except code that cannot suspend.
         *
         * It is deliberately not a `ThreadLocal.withInitial`: absent means absent, and a log written
         * outside any telemetry scope should say so rather than invent an empty one.
         */
        internal val mirror = ThreadLocal<TelemetryContext?>()
    }
}

/** What is happening on this thread right now, for the callers that cannot suspend to ask properly. */
internal fun threadContext(): TelemetryContext? = TelemetryContext.mirror.get()

/** The span this code is running in, or null outside any. */
fun currentSpan(): SpanContext? = threadContext()?.span

/** The `traceparent` to send with an outgoing call, or null when there is no trace to continue. */
fun currentTraceparent(): String? = currentSpan()?.traceparent()

/**
 * Runs [block] against [telemetry], instead of whatever `Telemetry.install` set.
 *
 * This is how a spec stays isolated — two specs in one JVM installing different roots would fight
 * over a global — and how an application that would rather pass its telemetry than install it can.
 */
suspend fun <T> withTelemetry(
    telemetry: Telemetry,
    block: suspend () -> T,
): T = withContext(TelemetryContext(telemetry, span = null, attributes = Attributes.EMPTY)) { block() }

/**
 * Adds attributes that everything inside [block] carries — logs, spans, and their spans in turn.
 *
 * ```kotlin
 * withAttributes("orderId" to order.id, "tenant" to tenant) {
 *     charge()      // every log and span in here says which order and which tenant
 * }
 * ```
 *
 * This is what an MDC is for, without being an MDC: the values travel with the coroutine, so they
 * are still right after a suspension, still right on a different dispatcher, and **absent** in a
 * sibling coroutine that was not launched inside this block — which is the case an MDC gets wrong
 * quietly.
 */
suspend fun <T> withAttributes(
    vararg pairs: Pair<String, Any?>,
    block: suspend () -> T,
): T {
    val current = coroutineContext[TelemetryContext] ?: return block()
    return withContext(current.with(attributes = current.attributes + attributesOf(*pairs))) { block() }
}
