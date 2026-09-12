package com.softistx.telemetry.otlp

import com.softistx.telemetry.Attributes
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.SpanRecord
import com.softistx.telemetry.model.SpanStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/** The instrumentation this came from, which is this library and not the application. */
internal const val SCOPE = "stx-telemetry"

/**
 * Turns a resource into OTLP's attribute list, under the names the semantic conventions give them.
 *
 * `service.name` is always written, even when nobody set a version or an environment: a backend that
 * cannot group by service has nothing to show.
 */
internal fun Resource.toOtlp(): OtlpResource =
    OtlpResource(
        buildList {
            add(KeyValue("service.name", AnyValue(stringValue = service)))
            version?.let { add(KeyValue("service.version", AnyValue(stringValue = it))) }
            environment?.let { add(KeyValue("deployment.environment.name", AnyValue(stringValue = it))) }
            addAll(attributes.toOtlp())
        },
    )

/**
 * Logs, grouped into one scope per source.
 *
 * OTLP's instrumentation scope is what a logger name is for, so `logger<CheckoutService>()` becomes
 * the scope rather than an attribute nobody's backend knows to index. Grouping is why this takes the
 * whole batch instead of one record at a time.
 */
internal fun logsDocument(
    resource: Resource,
    logs: List<LogRecord>,
): LogsDocument =
    LogsDocument(
        listOf(
            ResourceLogs(
                resource = resource.toOtlp(),
                scopeLogs =
                    logs.groupBy { it.source }.map { (source, records) ->
                        ScopeLogs(Scope(source), records.map { it.toOtlp() })
                    },
            ),
        ),
    )

internal fun tracesDocument(
    resource: Resource,
    spans: List<SpanRecord>,
): TracesDocument =
    TracesDocument(
        listOf(
            ResourceSpans(
                resource = resource.toOtlp(),
                scopeSpans = listOf(ScopeSpans(Scope(SCOPE), spans.map { it.toOtlp() })),
            ),
        ),
    )

private fun LogRecord.toOtlp(): OtlpLogRecord =
    OtlpLogRecord(
        timeUnixNano = at.unixNano(),
        severityNumber = severity.number,
        severityText = severity.name.uppercase(),
        body = AnyValue(stringValue = name),
        attributes = attributes.toOtlp() + (error?.toOtlp() ?: emptyList()),
        traceId = span?.traceId?.hex,
        spanId = span?.spanId?.hex,
    )

private fun SpanRecord.toOtlp(): OtlpSpan =
    OtlpSpan(
        traceId = context.traceId.hex,
        spanId = context.spanId.hex,
        parentSpanId = parent?.hex,
        name = name,
        kind = kind.number,
        startTimeUnixNano = startedAt.unixNano(),
        endTimeUnixNano = endedAt.unixNano(),
        attributes = attributes.toOtlp() + (error?.toOtlp() ?: emptyList()),
        events = events.map { OtlpEvent(it.at.unixNano(), it.name, it.attributes.toOtlp()) },
        status =
            OtlpStatus(
                code =
                    when (status) {
                        SpanStatus.Ok -> 1

                        SpanStatus.Error -> 2

                        // OTLP has no third code, and `unset` is closer to the truth than `error`:
                        // a cancelled span is work that stopped, not work that went wrong.
                        SpanStatus.Cancelled -> 0
                    },
                message = error?.message,
            ),
    )

/**
 * A failure, as the exception semantic conventions name it.
 *
 * These are attributes rather than a field of their own because that is where OTLP puts them, and
 * because it means a backend's existing "show me the errors" query finds ours without being told.
 */
private fun com.softistx.telemetry.model.ErrorInfo.toOtlp(): List<KeyValue> =
    buildList {
        add(KeyValue("exception.type", AnyValue(stringValue = type)))
        message?.let { add(KeyValue("exception.message", AnyValue(stringValue = it))) }
        stackTrace?.let { add(KeyValue("exception.stacktrace", AnyValue(stringValue = it))) }
    }

private fun Attributes.toOtlp(): List<KeyValue> = values.map { (key, value) -> KeyValue(key, value.toAnyValue()) }

/**
 * A JSON value onto OTLP's tagged union.
 *
 * An integer becomes `intValue`, which is a **string** in this encoding: the field is an `int64` and
 * JSON's number type cannot carry one intact. Anything with structure — an object, which
 * `stx-telemetry`'s rules say should never get here — is written as its own JSON text under
 * `stringValue`, so a value that slipped through is visible in a backend rather than dropped.
 */
internal fun JsonElement.toAnyValue(): AnyValue =
    when (this) {
        is JsonNull -> {
            AnyValue(stringValue = "")
        }

        is JsonPrimitive -> {
            when {
                isString -> {
                    AnyValue(stringValue = content)
                }

                content == "true" || content == "false" -> {
                    AnyValue(boolValue = content.toBoolean())
                }

                else -> {
                    content.toLongOrNull()?.let { AnyValue(intValue = it.toString()) }
                        ?: content.toDoubleOrNull()?.let { AnyValue(doubleValue = it) }
                        ?: AnyValue(stringValue = content)
                }
            }
        }

        is JsonArray -> {
            AnyValue(arrayValue = ArrayValue(map { it.toAnyValue() }))
        }

        is JsonObject -> {
            AnyValue(stringValue = toString())
        }
    }

/** Nanoseconds since the epoch, as a decimal string: OTLP's `fixed64`, which JSON writes quoted. */
private fun Instant.unixNano(): String = (epochSeconds * 1_000_000_000L + nanosecondsOfSecond).toString()
