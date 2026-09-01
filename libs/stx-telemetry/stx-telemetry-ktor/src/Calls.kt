package com.softistx.telemetry.ktor

import com.softistx.ktor.required
import com.softistx.telemetry.Telemetry
import com.softistx.telemetry.trace.SpanContext
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's telemetry, as [Observability] built or adopted it. */
val Application.telemetry: Telemetry get() = required(TelemetryKey, "Observability")

/** The same telemetry, from a route. It is shared and holds no per-request state. */
val ApplicationCall.telemetry: Telemetry get() = application.telemetry

/**
 * This request's server span, or null when [ObservabilityConfiguration.traced] said no.
 *
 * A route that wants the current span usually wants `currentSpan()` instead, which needs no call and
 * works three layers down. This is for the handler that has a call and no coroutine of its own.
 */
val ApplicationCall.span: SpanContext? get() = attributes.getOrNull(SpanKey)

/** The header to send with a call this request makes, so the next service continues the trace. */
val ApplicationCall.traceparent: String? get() = span?.traceparent()
