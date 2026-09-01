package com.softistx.telemetry.slf4j.fixture

import com.softistx.telemetry.export.Exporter
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Signal
import org.slf4j.ILoggerFactory
import org.slf4j.MDC
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import org.slf4j.Logger as Slf4jLogger

/** One line as SLF4J received it, with the MDC as it stood at that moment. */
data class Line(
    val logger: String,
    val level: Level,
    val message: String,
    val failure: Throwable?,
    val mdc: Map<String, String>,
)

/** An SLF4J binding that keeps what it was told, for the outbound half of the bridge. */
class RecordingLoggerFactory : ILoggerFactory {
    val lines = ConcurrentLinkedQueue<Line>()

    override fun getLogger(name: String): Slf4jLogger = Recording(name, lines)

    fun at(logger: String): List<Line> = lines.filter { it.logger == logger }
}

private class Recording(
    name: String,
    private val lines: ConcurrentLinkedQueue<Line>,
) : LegacyAbstractLogger() {
    init {
        this.name = name
    }

    override fun isTraceEnabled() = true

    override fun isDebugEnabled() = true

    override fun isInfoEnabled() = true

    override fun isWarnEnabled() = true

    override fun isErrorEnabled() = true

    override fun getFullyQualifiedCallerName(): String = Recording::class.java.name

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        message: String?,
        arguments: Array<out Any>?,
        failure: Throwable?,
    ) {
        lines +=
            Line(
                logger = name,
                level = level,
                message = MessageFormatter.basicArrayFormat(message, arguments),
                failure = failure,
                mdc = MDC.getCopyOfContextMap() ?: emptyMap(),
            )
    }
}

/** Keeps every signal the pipeline shipped, for the inbound half. */
class Collector : Exporter {
    private val signals = ConcurrentLinkedQueue<Signal>()

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        signals += batch
    }

    val logs: List<LogRecord> get() = signals.filterIsInstance<LogRecord>()

    fun log(name: String): LogRecord? = logs.firstOrNull { it.name == name }
}
