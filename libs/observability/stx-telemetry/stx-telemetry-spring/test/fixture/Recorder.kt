package com.softistx.telemetry.spring.fixture

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import org.slf4j.MDC
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * An SLF4J context the spec can read back, handed to `Slf4jExporter` as a bean.
 *
 * The bound factory would do for a spec that only wanted the exporter to exist, but not for one that
 * checks *what* it wrote: this module's test classpath binds SLF4J to `stx-telemetry`'s own provider,
 * so asking the real `LoggerFactory` for a logger would ask the pipeline to export into itself. The
 * `ILoggerFactory` bean is exactly the seam the auto-configuration offers for that.
 */
class Recorder : ILoggerFactory {
    private val recorded = ConcurrentLinkedQueue<Line>()

    override fun getLogger(name: String): Logger = Recording(name, recorded)

    val lines: List<Line> get() = recorded.toList()

    fun line(message: String): Line? = lines.firstOrNull { it.message == message }
}

/**
 * One call that reached an appender, with the MDC as it stood during it.
 *
 * [traceId] is read inside the call rather than after it, because the exporter takes its keys back
 * out in a `finally` — reading it later would find the MDC already clean and say the ids were never
 * there.
 */
data class Line(
    val logger: String,
    val level: Level,
    val message: String,
    val traceId: String?,
    val attribute: (String) -> String?,
)

private class Recording(
    // Not `name`: LegacyAbstractLogger has a protected field of that name, which this would hide.
    private val logger: String,
    private val recorded: ConcurrentLinkedQueue<Line>,
) : LegacyAbstractLogger() {
    override fun getName(): String = logger

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        message: String,
        arguments: Array<out Any>?,
        throwable: Throwable?,
    ) {
        val context = MDC.getCopyOfContextMap().orEmpty()
        recorded += Line(logger, level, message, context["traceId"], context::get)
    }

    override fun isTraceEnabled(): Boolean = true

    override fun isDebugEnabled(): Boolean = true

    override fun isInfoEnabled(): Boolean = true

    override fun isWarnEnabled(): Boolean = true

    override fun isErrorEnabled(): Boolean = true
}
