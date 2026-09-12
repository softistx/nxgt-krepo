package com.softistx.telemetry.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

/**
 * Makes `stx-telemetry` the thing SLF4J logs to.
 *
 * Put this module on the classpath and every library that logs through SLF4J — Lettuce, Hibernate,
 * the Ktor engine, the Kafka and Mongo drivers, Spring itself — writes into the same pipeline as
 * `logger<T>()`, carrying the current span. That is the point: a driver's "connection reset" and the
 * request it happened under are worth nothing to each other in two different files.
 *
 * ## This takes over SLF4J
 *
 * SLF4J binds **one** provider, found on the classpath. If logback or another binding is also
 * present, SLF4J prints a warning and picks one — which one is not something to leave to chance:
 *
 * ```
 * -Dslf4j.provider=com.softistx.telemetry.slf4j.TelemetryServiceProvider
 * ```
 *
 * An application that would rather keep logback should not depend on this module at all, and should
 * use [Slf4jExporter] instead — the same bridge, pointed the other way.
 *
 * ## What happens before a telemetry is installed
 *
 * Nothing is emitted, in silence. Logging is initialised by the first library that logs, which is
 * routinely earlier than an application's own startup; refusing, buffering or printing a warning
 * would each be worse than dropping a line that nobody had yet said where to send.
 */
class TelemetryServiceProvider : SLF4JServiceProvider {
    private val loggers = TelemetryLoggerFactory()
    private val markers = BasicMarkerFactory()

    /**
     * A working MDC, on purpose.
     *
     * It looks like a contradiction of everything this library argues, and it is not: third-party
     * blocking code that calls `MDC.put` has no other way to say anything, and on **its own thread,
     * synchronously, around its own log call** the MDC is correct. What is wrong is carrying it
     * across a suspension, which nothing here does. `TelemetryLogger` reads it for bridged records
     * only; `stx-telemetry`'s own loggers never look at it.
     */
    private val mdc = BasicMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggers

    override fun getMarkerFactory(): IMarkerFactory = markers

    override fun getMDCAdapter(): MDCAdapter = mdc

    override fun getRequestedApiVersion(): String = API_VERSION

    override fun initialize() = Unit

    internal companion object {
        /** The SLF4J API this was written against. SLF4J compares it and warns on a mismatch. */
        const val API_VERSION = "2.0.99"
    }
}
