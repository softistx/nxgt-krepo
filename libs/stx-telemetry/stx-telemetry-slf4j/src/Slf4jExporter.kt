package com.softistx.telemetry.slf4j

import com.softistx.telemetry.export.Exporter
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Severity
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanRecord
import com.softistx.telemetry.model.SpanStatus
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.ILoggerFactory
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * The bridge pointed the other way: `stx-telemetry`'s signals, written to SLF4J.
 *
 * For an application that already has logback, an appender fleet and a log pipeline it trusts, and
 * wants `span { }` and typed events without changing where anything ends up.
 *
 * ```kotlin
 * Telemetry("checkout") { export(Slf4jExporter()) }.install()
 * ```
 *
 * ## The trace id goes in the MDC, and here that is correct
 *
 * A logback pattern reads `%X{traceId}`, so that is where the ids have to be. It is the same MDC this
 * library argues against, used the one way it is safe: this runs on the pipeline's single consumer,
 * and the values are put and removed **around one synchronous call**, with no suspension between. The
 * MDC is wrong when it has to survive a hop; nothing here asks it to.
 *
 * ## Not with the provider
 *
 * [TelemetryServiceProvider] makes SLF4J write into `stx-telemetry`. This writes `stx-telemetry` into
 * SLF4J. Both at once is a loop, and it is a loop that would take the process down rather than
 * misbehave visibly — so this refuses to be built when the bound SLF4J provider is this module's own.
 */
class Slf4jExporter(
    /** Whether completed spans are logged too, one line each. Off for an application that only wants logs. */
    private val spans: Boolean = true,
    /** The level spans are logged at when they succeeded. A failed span is logged at `error`. */
    private val spanSeverity: Severity = Severity.Info,
    /**
     * Where the lines go. The bound one by default, which is the whole point of this exporter.
     *
     * It is a parameter because a spec needs to read back what was written, and because an
     * application with two SLF4J contexts — a plugin host, an embedded server — should be able to
     * say which one.
     */
    private val factory: ILoggerFactory = LoggerFactory.getILoggerFactory(),
) : Exporter {
    init {
        check(factory !is TelemetryLoggerFactory) {
            "SLF4J is bound to TelemetryServiceProvider, so exporting to SLF4J would feed this pipeline " +
                "back into itself. Use one direction or the other: drop stx-telemetry-slf4j's provider " +
                "from the classpath, or choose a binding with -Dslf4j.provider=…"
        }
    }

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        for (signal in batch) {
            when (signal) {
                is LogRecord -> write(signal)
                is SpanRecord -> if (spans) write(signal)
            }
        }
    }

    private fun write(record: LogRecord) {
        val logger = factory.getLogger(record.source)
        val failure = record.error
        withContext(record) {
            when (record.severity) {
                Severity.Debug -> logger.debug(record.name)
                Severity.Info -> logger.info(record.name)
                Severity.Warn -> if (failure == null) logger.warn(record.name) else logger.warn(record.name, failure.text())
                Severity.Error -> if (failure == null) logger.error(record.name) else logger.error(record.name, failure.text())
            }
        }
    }

    private fun write(record: SpanRecord) {
        val logger = factory.getLogger(SPANS)
        val line = "${record.name} ${record.endedAt - record.startedAt} ${record.status}"
        withContext(record) {
            when {
                record.status == SpanStatus.Error -> logger.error(line)
                spanSeverity == Severity.Debug -> logger.debug(line)
                spanSeverity == Severity.Warn -> logger.warn(line)
                spanSeverity == Severity.Error -> logger.error(line)
                else -> logger.info(line)
            }
        }
    }

    /**
     * Puts the ids and the attributes in the MDC for the length of one call, then takes them out.
     *
     * `finally` rather than `MDC.clear()`: the pipeline's thread is a pool thread that will be used
     * again, and a bridge that left keys behind would put this batch's order id on the next one's
     * lines — the exact failure this library exists to describe.
     */
    private inline fun withContext(
        signal: Signal,
        block: () -> Unit,
    ) {
        val keys = mutableListOf<String>()
        try {
            signal.span?.let {
                MDC.put("traceId", it.traceId.hex)
                MDC.put("spanId", it.spanId.hex)
                keys += listOf("traceId", "spanId")
            }
            for ((key, value) in signal.attributes.values) {
                MDC.put(key, (value as? JsonPrimitive)?.content ?: value.toString())
                keys += key
            }
            block()
        } finally {
            keys.forEach(MDC::remove)
        }
    }

    private companion object {
        /** Where spans are logged, since a span has no source of its own. */
        const val SPANS = "com.softistx.telemetry.span"

        /**
         * The failure, rebuilt just enough for an appender to print it.
         *
         * `ErrorInfo` flattened the original to strings when it was created — deliberately, so a
         * queue does not hold a `Throwable`'s view of the stack alive — so what goes to SLF4J is a
         * carrier with the recorded message and the recorded trace, not a lie about where it was
         * thrown.
         */
        fun com.softistx.telemetry.model.ErrorInfo.text(): Throwable = RecordedFailure(this)
    }
}

/** A failure that already happened, carried to an appender. Its stack trace is the recorded text. */
private class RecordedFailure(
    private val info: com.softistx.telemetry.model.ErrorInfo,
) : RuntimeException("${info.type}: ${info.message ?: ""}", null, false, false) {
    override fun toString(): String = message ?: info.type

    override fun printStackTrace(out: java.io.PrintStream) {
        out.println(info.stackTrace ?: toString())
    }

    override fun printStackTrace(out: java.io.PrintWriter) {
        out.println(info.stackTrace ?: toString())
    }
}
