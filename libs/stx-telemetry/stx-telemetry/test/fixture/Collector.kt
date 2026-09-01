package com.softistx.telemetry.fixture

import com.softistx.telemetry.Telemetry
import com.softistx.telemetry.export.Exporter
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Severity
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanRecord
import com.softistx.telemetry.trace.Sampler
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Keeps every signal so a spec can look at it. */
class Collector(
    private val slow: Duration = Duration.ZERO,
) : Exporter {
    private val signals = ConcurrentLinkedQueue<Signal>()

    var closed = false
        private set

    /** The resource the last batch was shipped for. */
    var seen: Resource? = null
        private set

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        seen = resource
        if (slow > Duration.ZERO) delay(slow)
        signals += batch
    }

    override fun close() {
        closed = true
    }

    val all: List<Signal> get() = signals.toList()
    val logs: List<LogRecord> get() = all.filterIsInstance<LogRecord>()
    val spans: List<SpanRecord> get() = all.filterIsInstance<SpanRecord>()

    fun span(name: String): SpanRecord? = spans.firstOrNull { it.name == name }

    fun log(name: String): LogRecord? = logs.firstOrNull { it.name == name }
}

/**
 * A telemetry that ships immediately, for a spec that wants to assert on the result.
 *
 * The linger is a millisecond rather than the default second, so a spec that closes right away is
 * asserting on the drain and not on the timer.
 */
fun collecting(
    collector: Collector,
    sampler: Sampler = Sampler.always,
    minimum: Severity = Severity.Debug,
    batch: Int = 512,
): Telemetry =
    Telemetry("spec") {
        this.sampler = sampler
        this.minimum = minimum
        this.batch = batch
        linger = 1.milliseconds
        stackTraces = false
        export(collector)
    }
