package com.softistx.telemetry.slf4j

import com.softistx.telemetry.logger
import com.softistx.telemetry.model.Severity
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter

/**
 * One SLF4J logger, writing into `stx-telemetry`.
 *
 * It extends `LegacyAbstractLogger`, which funnels SLF4J's forty-odd overloads — every arity of
 * placeholder, with and without a marker, with and without a throwable — into a single call. There
 * is no version of this worth writing by hand.
 *
 * The `{}` placeholders are filled by SLF4J's own `MessageFormatter`, so a bridged line reads exactly
 * as it would under logback, and the trailing `Throwable` is separated the way SLF4J separates it.
 */
class TelemetryLogger internal constructor(
    name: String,
) : LegacyAbstractLogger() {
    private val delegate = logger(name)

    init {
        this.name = name
    }

    override fun isTraceEnabled(): Boolean = enabled(Severity.Debug)

    override fun isDebugEnabled(): Boolean = enabled(Severity.Debug)

    override fun isInfoEnabled(): Boolean = enabled(Severity.Info)

    override fun isWarnEnabled(): Boolean = enabled(Severity.Warn)

    override fun isErrorEnabled(): Boolean = enabled(Severity.Error)

    /**
     * What SLF4J puts in a caller-location computation, and it must be this class.
     *
     * Naming anything else makes a logback-style `%L` pattern point at this bridge rather than at the
     * code that logged — which is the whole of what a caller name is for.
     */
    override fun getFullyQualifiedCallerName(): String = TelemetryLogger::class.java.name

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        message: String?,
        arguments: Array<out Any>?,
        failure: Throwable?,
    ) {
        val severity = level.severity()
        val text = MessageFormatter.basicArrayFormat(message, arguments)
        val attributes = mdcAttributes() + markerAttribute(marker)

        when {
            failure != null && severity == Severity.Error -> delegate.error(text, failure, *attributes)
            failure != null -> delegate.warn(text, failure, *attributes)
            severity == Severity.Debug -> delegate.debug(text, *attributes)
            severity == Severity.Info -> delegate.info(text, *attributes)
            severity == Severity.Warn -> delegate.warn(text, *attributes)
            else -> delegate.error(text, *attributes)
        }
    }

    private fun enabled(severity: Severity): Boolean = delegate.enabled(severity)

    private companion object {
        /**
         * SLF4J's five levels onto four.
         *
         * `TRACE` becomes `Debug` rather than gaining a level of its own: `stx-telemetry` refuses a
         * fifth on purpose, and a library's trace logging is debug logging by any other name.
         */
        fun Level.severity(): Severity =
            when (this) {
                Level.TRACE, Level.DEBUG -> Severity.Debug
                Level.INFO -> Severity.Info
                Level.WARN -> Severity.Warn
                Level.ERROR -> Severity.Error
            }

        /**
         * Whatever the caller put in the MDC, as attributes.
         *
         * Read here and nowhere else. On the thread that is writing the log, synchronously, having
         * been set by the same blocking code — which is the one situation where an MDC is right.
         */
        fun mdcAttributes(): Array<Pair<String, Any?>> =
            org.slf4j.MDC
                .getCopyOfContextMap()
                ?.map { (key, value) -> key to value }
                ?.toTypedArray()
                ?: emptyArray()

        fun markerAttribute(marker: Marker?): Array<Pair<String, Any?>> =
            if (marker == null) emptyArray() else arrayOf("marker" to marker.name)
    }
}
