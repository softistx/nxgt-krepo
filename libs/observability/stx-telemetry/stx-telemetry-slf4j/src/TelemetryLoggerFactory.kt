package com.softistx.telemetry.slf4j

import com.softistx.common.concurrent.Memo
import org.slf4j.ILoggerFactory
import org.slf4j.Logger

/**
 * One [TelemetryLogger] per name, for ever.
 *
 * SLF4J's contract is that `getLogger(name)` returns the same instance every time — callers hold it
 * in a `private static final` and compare nothing, but frameworks do reconfigure by name. A [Memo]
 * rather than a `Mutex`: this is called from static initialisers on whatever thread got there
 * first, which cannot suspend.
 *
 * "The same instance every time" is exactly what a non-atomic lookup would break, and break
 * invisibly — two loggers under one name, both working, one of them configured by nobody.
 */
class TelemetryLoggerFactory : ILoggerFactory {
    private val loggers = Memo<String, TelemetryLogger> { TelemetryLogger(it) }

    override fun getLogger(name: String): Logger = loggers[name]
}
