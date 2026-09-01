package com.strange.telemetry

import com.strange.telemetry.context.TelemetryContext
import com.strange.telemetry.model.ErrorInfo
import com.strange.telemetry.model.SpanEvent
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.model.SpanRecord
import com.strange.telemetry.model.SpanStatus
import com.strange.telemetry.trace.SpanContext
import com.strange.telemetry.trace.SpanId
import com.strange.telemetry.trace.TraceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

/**
 * Runs [block] as a span: a named piece of work with a start, an end, and an outcome.
 *
 * ```kotlin
 * span("charge", "orderId" to order.id) {
 *     log.info(Charged(order.id, amount))   // carries this span's traceId and spanId
 *     attribute("processor", gateway.name)
 *     payments.charge(order.card)
 * }
 * ```
 *
 * The span it opens becomes the current one **for this coroutine and everything launched inside it**,
 * and for nothing else. That is a consequence of the context being a `CoroutineContext.Element`
 * rather than a thread-local, and it is the behaviour an MDC gets wrong in both directions: it leaks
 * into whatever else runs on the thread, and it is missing after a suspension moves the work.
 *
 * [attributes] given here are inherited, exactly like [com.strange.telemetry.context.withAttributes]:
 * a log written inside carries them too, and so does a nested span. Attributes set on the receiver
 * with [SpanScope.attribute] belong to this span alone.
 *
 * ## What it does with a failure
 *
 * Nothing, except record it. The exception propagates unchanged — a span is an observer, and an
 * observer that swallows is worse than no observer. A [CancellationException] is recorded as
 * [SpanStatus.Cancelled] rather than an error, because a shutdown that cancels a scope should not
 * fill a dashboard with failures that describe nothing but the shutdown.
 *
 * ## With no telemetry installed
 *
 * [block] still runs, on an invalid span context, and nothing is emitted. A library that opens spans
 * must work in an application that has never heard of this one.
 */
suspend fun <T> span(
    name: String,
    vararg attributes: Pair<String, Any?>,
    kind: SpanKind = SpanKind.Internal,
    block: suspend SpanScope.() -> T,
): T {
    val parent = coroutineContext[TelemetryContext]
    val telemetry = parent?.telemetry ?: Telemetry.installed ?: return SpanScope(DETACHED).block()

    val traceId = parent?.span?.traceId ?: TraceId.random()
    val sampled = parent?.span?.sampled ?: telemetry.sampler.sample(traceId)
    val context = SpanContext(traceId, SpanId.random(), sampled)
    val inherited = (parent?.attributes ?: Attributes.EMPTY) + attributesOf(*attributes)
    val element =
        parent?.with(span = context, attributes = inherited)
            ?: TelemetryContext(telemetry, context, inherited)

    val scope = SpanScope(context)
    val startedAt = Clock.System.now()
    var status = SpanStatus.Ok
    var error: ErrorInfo? = null
    try {
        return withContext(element) { scope.block() }
    } catch (cancellation: CancellationException) {
        status = SpanStatus.Cancelled
        error = ErrorInfo.of(cancellation, stackTrace = false)
        throw cancellation
    } catch (failure: Throwable) {
        status = SpanStatus.Error
        error = ErrorInfo.of(failure, telemetry.stackTraces)
        throw failure
    } finally {
        if (sampled) {
            telemetry.emit(
                SpanRecord(
                    name = name,
                    context = context,
                    parent = parent?.span?.spanId,
                    kind = kind,
                    startedAt = startedAt,
                    endedAt = Clock.System.now(),
                    status = if (error == null) scope.status else status,
                    attributes = inherited + scope.snapshot(),
                    events = scope.events(),
                    error = error,
                ),
            )
        }
    }
}

/**
 * Continues a trace that arrived over the wire.
 *
 * ```kotlin
 * server(call.request.header("traceparent"), "GET /orders", kind = SpanKind.Server) { … }
 * ```
 *
 * A malformed or absent header starts a fresh trace rather than failing: see
 * [SpanContext.Companion.traceparent]. This is what the Ktor and Spring integrations call, and it is
 * public because a service that speaks some other protocol needs it too.
 */
suspend fun <T> continuing(
    traceparent: String?,
    name: String,
    vararg attributes: Pair<String, Any?>,
    kind: SpanKind = SpanKind.Server,
    block: suspend SpanScope.() -> T,
): T {
    val remote = SpanContext.traceparent(traceparent)
    val telemetry = coroutineContext[TelemetryContext]?.telemetry ?: Telemetry.installed
    if (remote == null || telemetry == null) {
        return span(name, *attributes, kind = kind, block = block)
    }
    return withContext(TelemetryContext(telemetry, remote, Attributes.EMPTY)) {
        span(name, *attributes, kind = kind, block = block)
    }
}

/**
 * The receiver inside [span]: what this particular span carries, and what happened during it.
 *
 * Its state is held in concurrent collections rather than behind a `Mutex`, so that [attribute] and
 * [event] stay ordinary non-suspending calls. They have to be: a span's block routinely launches
 * work, and a suspending `attribute` could not be called from a callback inside it.
 */
class SpanScope internal constructor(
    /** This span's identity — what a `traceparent` for an outgoing call is built from. */
    val context: SpanContext,
) {
    private val extra = ConcurrentHashMap<String, JsonElement>()
    private val moments = ConcurrentLinkedQueue<SpanEvent>()

    /**
     * The outcome, when the block does not throw.
     *
     * Set it to [SpanStatus.Error] for work that failed without an exception — a call that returned
     * a 500, a validation that came back refused. A thrown exception overrides whatever is here.
     */
    @Volatile
    var status: SpanStatus = SpanStatus.Ok

    val traceId: TraceId get() = context.traceId
    val spanId: SpanId get() = context.spanId

    /** The header value to send with a call this span makes, so the next service continues the trace. */
    fun traceparent(): String = context.traceparent()

    fun attribute(
        name: String,
        value: Any?,
    ) {
        extra[name] = attribute(value)
    }

    fun attributes(vararg pairs: Pair<String, Any?>) {
        pairs.forEach { (name, value) -> extra[name] = attribute(value) }
    }

    /** A moment inside the span, for when a log would be too much and an attribute too little. */
    fun event(
        name: String,
        vararg attributes: Pair<String, Any?>,
    ) {
        moments += SpanEvent(name, Clock.System.now(), attributesOf(*attributes))
    }

    /** The typed form, on the same argument as [Logger]: the serial name names it, the fields are its attributes. */
    inline fun <reified T : Any> event(event: T) = event(event, serializer())

    @PublishedApi
    internal fun <T> event(
        event: T,
        serializer: KSerializer<T>,
    ) {
        val encoded =
            try {
                spanJson.encodeToJsonElement(serializer, event)
            } catch (_: SerializationException) {
                null
            }
        moments +=
            SpanEvent(
                serializer.descriptor.serialName,
                Clock.System.now(),
                (encoded as? JsonObject)?.let { Attributes(it) } ?: Attributes.EMPTY,
            )
    }

    internal fun snapshot(): Attributes = if (extra.isEmpty()) Attributes.EMPTY else Attributes(extra.toMap())

    internal fun events(): List<SpanEvent> = moments.toList()
}

private val spanJson = Json { encodeDefaults = true }

/** The context a span gets when nothing is listening: valid to read, never exported. */
private val DETACHED = SpanContext(TraceId.INVALID, SpanId.INVALID, sampled = false)
