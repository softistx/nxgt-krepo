package com.strange.telemetry.ktor

import com.strange.ktor.resource
import com.strange.telemetry.Telemetry
import com.strange.telemetry.context.withTelemetry
import com.strange.telemetry.continuing
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.model.SpanStatus
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingRoot
import io.ktor.util.AttributeKey

/**
 * One telemetry per application, and a server span per request.
 *
 * ```kotlin
 * install(Observability) {
 *     service = "checkout"
 *     environment = "production"
 *     export(OtlpExporter("http://otel-collector:4318"))
 * }
 *
 * get("/orders/{id}") {
 *     log.info(Fetched(call.parameters["id"]!!))   // carries this request's trace
 * }
 * ```
 *
 * The span **wraps the rest of the pipeline** rather than being opened and closed by two separate
 * hooks, which is the only way a `CoroutineContext.Element` can be in scope for the handler: the
 * plugin intercepts at [ApplicationCallPipeline.Monitoring] and calls `proceed()` inside
 * [continuing]. Everything the route does — including every coroutine it launches and every library
 * that logs through SLF4J — is inside that span.
 *
 * A `traceparent` on the way in is continued, so a request that crossed two services is one trace.
 * A malformed or absent one starts a fresh trace rather than failing a request that is otherwise
 * fine.
 *
 * ## Why it is called Observability and not Telemetry
 *
 * `Telemetry` is the class this plugin builds, and a file that installs the plugin usually wants the
 * type as well — `val telemetry: Telemetry = application.telemetry`. Two identifiers spelled the
 * same in one file is a name clash the application has to work around, so the plugin took the other
 * word: telemetry is the data, observability is what an application switches on.
 */
val Observability =
    createApplicationPlugin(name = "Observability", createConfiguration = ::ObservabilityConfiguration) {
        val configuration = pluginConfig
        val telemetry =
            application.resource(TelemetryKey, configuration.instance) {
                val service =
                    requireNotNull(configuration.service) {
                        "install(Observability) needs `service`, or an `instance` built elsewhere"
                    }
                configuration.build(service)
            }

        if (configuration.install) telemetry.install()
        if (configuration.injectable) application.provideTelemetry()

        // Routing resolves the template long after the span was named, and the event is the only
        // place it is public. Recorded on the call, read back when the span is about to be written.
        application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
            call.attributes.put(RouteKey, call.template())
        }

        application.intercept(ApplicationCallPipeline.Monitoring) {
            if (!configuration.traced(call)) return@intercept proceed()

            val request = call
            withTelemetry(telemetry) {
                continuing(
                    request.request.headers[TRACEPARENT],
                    configuration.spanName(request),
                    "http.request.method" to request.request.httpMethod.value,
                    "url.path" to request.request.path(),
                    kind = SpanKind.Server,
                ) {
                    request.attributes.put(SpanKey, context)
                    try {
                        proceed()
                    } finally {
                        val route = request.attributes.getOrNull(RouteKey)
                        if (route != null) {
                            name = "${request.request.httpMethod.value} $route"
                            attribute("http.route", route)
                        }
                        // Absent when the block threw: the response has not been produced yet, and a
                        // StatusPages handler will make one further out, after this span is written.
                        // The failure is on the span either way, which is the part worth having.
                        val status = request.response.status()?.value
                        if (status != null) {
                            attribute("http.response.status_code", status)
                            // 5xx is this service failing; 4xx is a caller being told no, which is
                            // the service working. Colouring both red makes the dashboard useless.
                            if (status >= 500) this.status = SpanStatus.Error
                        }
                    }
                }
            }
        }
    }

private const val TRACEPARENT = "traceparent"

/** The route template, rebuilt from the node chain: Ktor prints a node with its selector attached. */
private fun RoutingCall.template(): String = route.parent?.toString() ?: route.toString()

internal val TelemetryKey = AttributeKey<Telemetry>("stx.telemetry")
internal val SpanKey = AttributeKey<com.strange.telemetry.trace.SpanContext>("stx.telemetry.span")
internal val RouteKey = AttributeKey<String>("stx.telemetry.route")

internal fun ObservabilityConfiguration.build(service: String): Telemetry =
    Telemetry(service) {
        version = this@build.version
        environment = this@build.environment
        attributes = this@build.attributes
        sampler = this@build.sampler
        minimum = this@build.minimum
        stackTraces = this@build.stackTraces
        batch = this@build.batch
        linger = this@build.linger
        drainTimeout = this@build.drainTimeout
        onExportError = this@build.onExportError
        this@build.exporters.forEach { export(it) }
    }
