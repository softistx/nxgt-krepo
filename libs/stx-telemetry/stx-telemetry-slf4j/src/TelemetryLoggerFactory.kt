package com.strange.telemetry.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * One [TelemetryLogger] per name, for ever.
 *
 * SLF4J's contract is that `getLogger(name)` returns the same instance every time — callers hold it
 * in a `private static final` and compare nothing, but frameworks do reconfigure by name. A
 * `ConcurrentHashMap` rather than a `Mutex`: this is called from static initialisers on whatever
 * thread got there first, which cannot suspend.
 */
class TelemetryLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, TelemetryLogger>()

    override fun getLogger(name: String): Logger = loggers.computeIfAbsent(name) { TelemetryLogger(it) }
}
