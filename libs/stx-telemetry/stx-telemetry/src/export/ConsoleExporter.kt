package com.softistx.telemetry.export

import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanRecord
import com.softistx.telemetry.model.SpanStatus
import kotlinx.serialization.json.JsonPrimitive
import java.io.PrintStream
import kotlin.time.Instant

/**
 * One line per signal, for a person reading a terminal.
 *
 * ```
 * 14:02:11.418 INFO  CheckoutService  Charged  orderId=A-91 amount=4999  [4bf92f…/00f067]
 * 14:02:11.502 SPAN  charge  62ms OK  orderId=A-91 processor=stripe  [4bf92f…/00f067]
 * ```
 *
 * It is the default an application gets in development, and it is deliberately *not* the format a
 * machine reads — that is [JsonLinesExporter]. Trying to be both is how a log format ends up bad at
 * both: the trace id is abbreviated here because a human scanning a column needs to tell two traces
 * apart, not to copy one.
 *
 * Everything goes to one stream, `System.out` by default, including errors. A stream per severity
 * interleaves unpredictably when both are a terminal, which reorders the very lines somebody is
 * reading to work out what happened first.
 */
class ConsoleExporter(
    private val out: PrintStream = System.out,
    /** Whether a failure's stack trace is printed under its line. Off makes a terminal readable. */
    private val stackTraces: Boolean = true,
) : Exporter {
    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        for (signal in batch) out.println(render(signal))
        out.flush()
    }

    private fun render(signal: Signal): String =
        when (signal) {
            is LogRecord -> {
                buildString {
                    append(signal.at.clock()).append(' ')
                    append(
                        signal.severity.name
                            .uppercase()
                            .padEnd(5),
                    ).append(' ')
                    append(signal.source.substringAfterLast('.')).append("  ")
                    append(signal.name)
                    appendAttributes(signal)
                    appendTrace(signal)
                    signal.error?.let { failure ->
                        append("\n  ").append(failure.type)
                        failure.message?.let { append(": ").append(it) }
                        if (stackTraces) failure.stackTrace?.let { append('\n').append(it.prependIndent("  ")) }
                    }
                }
            }

            is SpanRecord -> {
                buildString {
                    append(signal.endedAt.clock()).append(' ')
                    append("SPAN ").append(' ')
                    append(signal.name).append("  ")
                    append(signal.endedAt - signal.startedAt)
                    if (signal.status != SpanStatus.Ok) append(' ').append(signal.status.name.uppercase())
                    appendAttributes(signal)
                    appendTrace(signal)
                }
            }
        }

    private fun StringBuilder.appendAttributes(signal: Signal) {
        if (signal.attributes.isEmpty) return
        append("  ")
        signal.attributes.values.entries.joinTo(this, separator = " ") { (name, value) ->
            "$name=${(value as? JsonPrimitive)?.content ?: value}"
        }
    }

    private fun StringBuilder.appendTrace(signal: Signal) {
        val span = signal.span ?: return
        append("  [")
            .append(span.traceId.hex.take(8))
            .append('/')
            .append(span.spanId.hex.take(8))
            .append(']')
    }

    /** Wall-clock time of day, because a date on every line of a terminal is a date nobody reads. */
    private fun Instant.clock(): String = toString().substringAfter('T').take(12).padEnd(12, '0')
}
