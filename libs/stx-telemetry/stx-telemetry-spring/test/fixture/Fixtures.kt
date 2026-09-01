package com.strange.telemetry.spring.fixture

import com.strange.telemetry.Telemetry
import com.strange.telemetry.context.currentSpan
import com.strange.telemetry.export.Exporter
import com.strange.telemetry.logger
import com.strange.telemetry.model.LogRecord
import com.strange.telemetry.model.Resource
import com.strange.telemetry.model.Severity
import com.strange.telemetry.model.Signal
import com.strange.telemetry.model.SpanRecord
import com.strange.telemetry.trace.SpanContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds

class Collector : Exporter {
    private val signals = ConcurrentLinkedQueue<Signal>()

    var closed = false
        private set

    override fun close() {
        closed = true
    }

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        signals += batch
    }

    val all: List<Signal> get() = signals.toList()
    val spans: List<SpanRecord> get() = all.filterIsInstance<SpanRecord>()
    val logs: List<LogRecord> get() = all.filterIsInstance<LogRecord>()

    fun span(name: String): SpanRecord? = spans.firstOrNull { it.name == name }

    fun log(name: String): LogRecord? = logs.firstOrNull { it.name == name }
}

fun collecting(collector: Collector): Telemetry =
    Telemetry("spec") {
        minimum = Severity.Debug
        linger = 1.milliseconds
        stackTraces = false
        export(collector)
    }

private val log = logger("orders")

/**
 * A suspending controller, which is the whole point of the spec that uses it.
 *
 * A `WebFilter` returning a `Mono` cannot put a `CoroutineContext.Element` in scope for a method like
 * this one — the handler is invoked as a coroutine of its own, and only what Spring carries across
 * reaches it. [seen] is what the filter did or did not manage to hand over.
 */
@RestController
class Orders {
    @Volatile
    var seen: SpanContext? = null

    @GetMapping("/orders/{id}")
    suspend fun order(
        @PathVariable id: String,
    ): String {
        seen = currentSpan()
        log.info("fetched", "orderId" to id)
        return "ok"
    }

    @GetMapping("/health")
    suspend fun health(): String = "up"

    @GetMapping("/boom")
    suspend fun boom(): String = throw IllegalStateException("gateway down")
}
