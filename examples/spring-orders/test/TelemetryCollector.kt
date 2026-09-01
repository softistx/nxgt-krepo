package com.softistx.example.orders

import com.softistx.telemetry.export.Exporter
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanRecord
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Where the suite's signals go, and the seam this file is really here to show.
 *
 * `stx.telemetry.otlp` and `stx.telemetry.mongo` are properties because those two destinations are
 * common enough not to be worth a `@Bean` method. Every other destination is **an `Exporter` bean**:
 * the auto-configuration adds every one it finds, so an application with somewhere of its own to put
 * its telemetry declares one and configures nothing. This is that, holding the signals in memory
 * instead of shipping them.
 *
 * It is also what the `test` profile substitutes for the Mongo exporter, which is off there because
 * its client is its own and no bean redirects it — `testResources/application-test.yaml` argues
 * that at the key.
 *
 * ## It sees the whole module, which is why the finders take a predicate
 *
 * Spring caches one context for every spec here, so this bean outlives any one of them — and a batch
 * lingers up to `stx.telemetry.linger` before it ships, so signals from the spec that ran *before*
 * can arrive after [clear]. Looking one up by name alone therefore finds whichever request got there
 * first, which is how a spec comes to assert on somebody else's 404. It did, once, before the
 * finders below took a predicate.
 *
 * So a caller narrows to its own: `TelemetryTest` gives every scenario its own order reference and
 * matches on it. A collector in a real application has the same problem for the same reason — it is
 * watching a process, not a test.
 */
class TelemetryCollector : Exporter {
    private val signals = ConcurrentLinkedQueue<Signal>()

    /** The resource the last batch was shipped for — `service`, `version`, `environment`. */
    @Volatile
    var resource: Resource? = null
        private set

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        this.resource = resource
        signals += batch
    }

    val spans: List<SpanRecord> get() = signals.filterIsInstance<SpanRecord>()
    val logs: List<LogRecord> get() = signals.filterIsInstance<LogRecord>()

    fun span(
        name: String,
        where: (SpanRecord) -> Boolean = { true },
    ): SpanRecord? = spans.firstOrNull { it.name == name && where(it) }

    fun log(
        name: String,
        where: (LogRecord) -> Boolean = { true },
    ): LogRecord? = logs.firstOrNull { it.name == name && where(it) }

    /**
     * Between scenarios.
     *
     * The context is cached for the whole module, so this bean outlives any one spec — the same
     * reason `template.clear("orders", "audits")` is in a `beforeEach` rather than a spec teardown.
     */
    fun clear() = signals.clear()
}

/**
 * The one bean the test sources contribute.
 *
 * Found by the application's own component scan, because these specs live in
 * `com.softistx.example.orders` — the package `@SpringBootApplication` scans — and the test output is
 * on the classpath it scans. That is why it is a plain `@Configuration` and not a `@TestConfiguration`
 * somebody has to `@Import`: an `@Import` on a spec class changes the test context's cache key, and
 * this module would start a second application to hold one bean.
 */
@Configuration(proxyBeanMethods = false)
class TelemetryTestConfiguration {
    @Bean
    fun telemetryCollector(): TelemetryCollector = TelemetryCollector()
}
