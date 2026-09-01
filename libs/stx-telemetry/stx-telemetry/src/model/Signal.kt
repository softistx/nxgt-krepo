package com.strange.telemetry.model

import com.strange.telemetry.Attributes
import com.strange.telemetry.trace.SpanContext
import com.strange.telemetry.trace.SpanId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * One thing that happened, on its way to an exporter.
 *
 * Logs and spans are one sealed type because everything downstream treats them the same way: they
 * queue together, batch together, and are dropped together when a pipeline is closed. Only the
 * exporter cares which it has, and OTLP puts them on two different endpoints — which is exactly one
 * `when` in one place.
 */
@Serializable
sealed interface Signal {
    val at: Instant
    val attributes: Attributes

    /** The span this belongs to, or null for a log written outside any span. */
    val span: SpanContext?
}

/**
 * How bad it is.
 *
 * Four levels, and deliberately no `trace`. The word already means something else in this library,
 * and a fifth level below `debug` is one nobody agrees the meaning of — every codebase that has one
 * has an argument about where the line is. Something too fine for `debug` is an attribute on a span,
 * which is where it can be read next to the work it describes.
 */
@Serializable
enum class Severity(
    /** The OpenTelemetry severity number, which is what an OTLP document carries. */
    val number: Int,
) {
    Debug(5),
    Info(9),
    Warn(13),
    Error(17),
}

/** What this process is, as every signal it emits reports it. */
@Serializable
data class Resource(
    /** `service.name` in OTLP. The one attribute every backend groups by, so it has no default. */
    val service: String,
    val version: String? = null,
    /** `deployment.environment.name` — "production", "staging". */
    val environment: String? = null,
    val attributes: Attributes = Attributes.EMPTY,
)

@Serializable
@SerialName("log")
data class LogRecord(
    override val at: Instant,
    val severity: Severity,
    /** The event's name: a `@Serializable` type's `serialName`, or the message passed at the call site. */
    val name: String,
    /** Where it was written from — `logger<CheckoutService>()` gives the class's qualified name. */
    val source: String,
    override val attributes: Attributes = Attributes.EMPTY,
    override val span: SpanContext? = null,
    val error: ErrorInfo? = null,
) : Signal

/** Whether the work a span covers succeeded. */
@Serializable
enum class SpanStatus {
    Ok,
    Error,

    /**
     * The coroutine was cancelled, which is neither.
     *
     * A shutdown that cancels a scope would otherwise fill a dashboard with errors that describe
     * nothing but the shutdown, and a timeout would be indistinguishable from the failure it was
     * meant to prevent.
     */
    Cancelled,
}

/** What kind of work a span covers, as OTLP names them. */
@Serializable
enum class SpanKind(
    val number: Int,
) {
    Internal(1),
    Server(2),
    Client(3),
    Producer(4),
    Consumer(5),
}

@Serializable
@SerialName("span")
data class SpanRecord(
    val name: String,
    val context: SpanContext,
    val parent: SpanId? = null,
    val kind: SpanKind = SpanKind.Internal,
    val startedAt: Instant,
    val endedAt: Instant,
    val status: SpanStatus = SpanStatus.Ok,
    override val attributes: Attributes = Attributes.EMPTY,
    val events: List<SpanEvent> = emptyList(),
    val error: ErrorInfo? = null,
) : Signal {
    override val at: Instant get() = startedAt
    override val span: SpanContext get() = context
}

/** A moment inside a span, when a log would be too much and an attribute too little. */
@Serializable
data class SpanEvent(
    val name: String,
    val at: Instant,
    val attributes: Attributes = Attributes.EMPTY,
)

/**
 * A failure, flattened.
 *
 * The stack trace is a string because that is what it is everywhere it goes, and rendering it once
 * at the point of failure is cheaper than carrying a `Throwable` through a queue that may outlive
 * the scope it came from — a `Throwable` holds references to whatever was on the stack.
 */
@Serializable
data class ErrorInfo(
    val type: String,
    val message: String? = null,
    val stackTrace: String? = null,
) {
    companion object {
        fun of(
            failure: Throwable,
            stackTrace: Boolean = true,
        ): ErrorInfo =
            ErrorInfo(
                type = failure::class.qualifiedName ?: failure::class.simpleName ?: "Throwable",
                message = failure.message,
                stackTrace = if (stackTrace) failure.stackTraceToString() else null,
            )
    }
}
