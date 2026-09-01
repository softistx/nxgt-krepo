package com.strange.telemetry

import com.strange.telemetry.export.Exporter
import com.strange.telemetry.export.Pipeline
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Severity
import com.strange.telemetry.model.Signal
import com.strange.telemetry.trace.Sampler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The root: what this process is, what it keeps, and where its signals go.
 *
 * ```kotlin
 * val telemetry = Telemetry("checkout") {
 *     version = "1.4.0"
 *     environment = "production"
 *     sampler = Sampler.ratio(0.1)
 *     export(ConsoleExporter())
 * }.install()
 * ```
 *
 * One per process, normally, and [install] is what makes `logger<T>()` and `span { }` find it
 * without being handed it. That global is the one concession to convenience in this library, and it
 * is a safe one for a reason worth being precise about: it is read only as a **fallback**, after the
 * coroutine context has been asked. A test — or an application that would rather pass its telemetry
 * around — uses [com.strange.telemetry.context.withTelemetry] and never touches the global at all.
 *
 * It is [AutoCloseable] and closing it drains: see [Pipeline.close].
 */
class Telemetry internal constructor(
    val resource: Resource,
    internal val sampler: Sampler,
    /** Logs below this are not built and not queued. Spans are governed by [sampler], not by this. */
    val minimum: Severity,
    internal val stackTraces: Boolean,
    private val pipeline: Pipeline,
) : AutoCloseable {
    internal fun emit(signal: Signal) {
        pipeline.post(signal)
    }

    /** Makes this the telemetry that code outside any [com.strange.telemetry.context.withTelemetry] finds. */
    fun install(): Telemetry = apply { installed = this }

    /**
     * Drains the queue, ships it, and closes the exporters. Safe to call twice.
     *
     * It also stands down as the installed default, so a process that closes its telemetry does not
     * leave every later log pointing at a pipeline that will never ship again.
     */
    override fun close() {
        if (installed === this) installed = null
        pipeline.close()
    }

    companion object {
        /**
         * The process-wide default, or null before anything has been installed.
         *
         * Null is a real answer and every caller handles it: a log written before startup, or in a
         * library used by an application that has no telemetry, is dropped in silence. **A logging
         * call is never allowed to be the thing that fails.**
         */
        @Volatile
        var installed: Telemetry? = null
            private set
    }
}

/**
 * Builds a [Telemetry] for [service].
 *
 * [service] has no default because `service.name` is the one attribute every backend groups by, and
 * a fleet of processes all called "unknown_service" is a fleet with no telemetry.
 */
fun Telemetry(
    service: String,
    block: TelemetryBuilder.() -> Unit = {},
): Telemetry = TelemetryBuilder(service).apply(block).build()

/** The receiver of [Telemetry]'s configuration block. */
class TelemetryBuilder internal constructor(
    private val service: String,
) {
    /** The build's version, as `service.version`. */
    var version: String? = null

    /** `production`, `staging` — whatever this deployment is called. */
    var environment: String? = null

    /** Attributes every signal from this process carries: a region, a pod name, a tenant. */
    var attributes: Attributes = Attributes.EMPTY

    /** Which traces are kept. Asked once per trace, at its root. */
    var sampler: Sampler = Sampler.always

    /** The lowest severity that is emitted at all. */
    var minimum: Severity = Severity.Info

    /**
     * Whether a failure's stack trace is rendered into the signal.
     *
     * On by default because a stack trace is the point of logging a failure, and off is there for a
     * deployment whose bill is per byte.
     */
    var stackTraces: Boolean = true

    /** How many signals ship together at most. */
    var batch: Int = 512

    /** How long a partial batch waits for company before it ships anyway. */
    var linger: Duration = 1.seconds

    /** How long [Telemetry.close] will wait for the queue to drain before giving up on it. */
    var drainTimeout: Duration = 10.seconds

    /**
     * What to do when an exporter throws.
     *
     * The default prints to `System.err`, which is the one destination that cannot itself be the
     * thing that is broken. Routing it back into this library would be a loop.
     */
    var onExportError: (Throwable) -> Unit = { it.printStackTrace() }

    private val exporters = mutableListOf<Exporter>()

    /** Adds a destination. Every signal goes to every one of them, in the order they were added. */
    fun export(exporter: Exporter) {
        exporters += exporter
    }

    internal fun build(): Telemetry {
        require(batch > 0) { "batch must be positive, was $batch" }
        require(linger.isPositive()) { "linger must be positive, was $linger" }
        return Telemetry(
            resource = Resource(service, version, environment, attributes),
            sampler = sampler,
            minimum = minimum,
            stackTraces = stackTraces,
            pipeline = Pipeline(exporters.toList(), batch, linger, drainTimeout, onExportError),
        )
    }
}
