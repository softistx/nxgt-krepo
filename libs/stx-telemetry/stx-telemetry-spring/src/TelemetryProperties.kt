package com.strange.telemetry.spring

import com.strange.telemetry.model.Severity
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.util.unit.DataSize
import java.time.Duration

/**
 * `stx.telemetry` — what this application reports itself as, and how much of it is kept.
 *
 * `service` is the only setting worth thinking about twice: it is the attribute every backend groups
 * by, and it falls back to `spring.application.name` rather than to a placeholder, because a fleet
 * of processes all called `unknown_service` is a fleet with no telemetry.
 */
@ConfigurationProperties(prefix = "stx.telemetry")
data class TelemetryProperties(
    val enabled: Boolean = false,
    /** `service.name`. Falls back to `spring.application.name` when unset. */
    val service: String? = null,
    /** `service.version`. Falls back to the implementation version on the main class's jar. */
    val version: String? = null,
    /** `deployment.environment.name` — "production", "staging". */
    val environment: String? = null,
    /** The lowest severity emitted at all. Spans are governed by [sampleRatio], not by this. */
    val minimum: Severity = Severity.Info,
    /** The share of traces kept, decided by trace id so two services at the same ratio agree. */
    val sampleRatio: Double = 1.0,
    val stackTraces: Boolean = true,
    val batch: Int = 512,
    val linger: Duration = Duration.ofSeconds(1),
    /** How long shutdown waits for the queue before giving up on it. */
    val drainTimeout: Duration = Duration.ofSeconds(10),
    /** Adds a `ConsoleExporter`, which is what you want in development and not in production. */
    val console: Boolean = false,
    /** Adds a `JsonLinesExporter` on stdout, for a collector that reads the container's log. */
    val jsonLines: Boolean = false,
    /** Whether each request becomes a server span. */
    val webFilter: Boolean = true,
    /**
     * Paths that get no span, matched by prefix.
     *
     * A health check answered every second by a load balancer is a trace nobody will read and most
     * of the traces there are.
     */
    val ignore: List<String> = emptyList(),
)

/** `stx.telemetry.otlp` — where the signals go, when they go anywhere. */
@ConfigurationProperties(prefix = "stx.telemetry.otlp")
data class TelemetryOtlpProperties(
    val enabled: Boolean = false,
    /** The collector's base URL. `/v1/logs` and `/v1/traces` are appended. */
    val endpoint: String = "http://localhost:4318",
    /** Sent on every request — an API key, a tenant header. */
    val headers: Map<String, String> = emptyMap(),
    val timeout: Duration = Duration.ofSeconds(10),
    /** How many times one document is sent before giving up. 1 disables retrying. */
    val attempts: Int = 3,
    /** The first wait between attempts; it doubles each time. */
    val backoff: Duration = Duration.ofMillis(500),
    val gzip: Boolean = true,
)

/**
 * `stx.telemetry.slf4j` — the signals written back out to the application's own logging.
 *
 * For an application that already has logback, an appender fleet and a log pipeline it trusts, and
 * wants `span { }` and typed events without changing where anything ends up. It is the opposite
 * direction from `stx-telemetry-slf4j`'s `SLF4JServiceProvider`, which is classpath-driven and has
 * no key here because there is nothing to decide: a provider is bound or it is not.
 *
 * **The two directions at once are a loop**, and `Slf4jExporter` refuses to be built when it finds
 * one — so turning this on with the provider also bound fails the context at startup rather than
 * taking the process down later.
 */
@ConfigurationProperties(prefix = "stx.telemetry.slf4j")
data class TelemetrySlf4jProperties(
    val enabled: Boolean = false,
    /** Whether completed spans are logged too, one line each. Off for an application that only wants logs. */
    val spans: Boolean = true,
    /** The level a span that succeeded is logged at. One that failed is always logged at `error`. */
    val spanSeverity: Severity = Severity.Info,
)

/**
 * `stx.telemetry.file` — a rotating file on the local disk.
 *
 * For a deployment with no collector — one VPS, an appliance, a job that has to leave evidence
 * behind — and for a container whose stdout is already crowded with somebody else's output. The
 * lines are the ones `stx.telemetry.json-lines` writes, so the same parser reads both.
 *
 * [maxSize] and [every] are both on by default and answer different questions: one bounds the disk,
 * the other bounds how old the newest closed file is. `0` turns either off.
 */
@ConfigurationProperties(prefix = "stx.telemetry.file")
data class TelemetryFileProperties(
    val enabled: Boolean = false,
    /** Where the active file is. Its parent directories are created if they are missing. */
    val path: String = "logs/telemetry.jsonl",
    /** The size at which the file is rolled — `64MB`, `512KB`. `0` for no size limit. */
    val maxSize: DataSize = DataSize.ofMegabytes(64),
    /**
     * The period one file covers, aligned to the epoch: `24h` rolls at UTC midnight rather than a
     * day after this process started. `0` for no time limit.
     */
    val every: Duration = Duration.ofHours(24),
    /** How many rolled files survive. `0` keeps only the file being written. */
    val keep: Int = 7,
    /** Whether a rolled file is gzipped. Off by default: it happens on the export path. */
    val compress: Boolean = false,
)

/**
 * `stx.telemetry.mongo` — signals into a MongoDB collection, with retention as a TTL index.
 *
 * The connection is **this exporter's own**, built from [uri] and closed with the context, rather
 * than the application's. That is deliberate and not an oversight: a burst of telemetry on the pool
 * the business requests are queueing for turns an observability problem into an outage.
 */
@ConfigurationProperties(prefix = "stx.telemetry.mongo")
data class TelemetryMongoProperties(
    val enabled: Boolean = false,
    /** The connection string for telemetry's own client. */
    val uri: String = "mongodb://localhost:27017",
    val database: String = "telemetry",
    val collection: String = "telemetry",
    /** How long a signal is kept, as a TTL index Mongo enforces itself. `0` keeps everything. */
    val retention: Duration = Duration.ofDays(30),
)
