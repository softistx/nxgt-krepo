package com.softistx.telemetry.otlp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * OTLP's JSON documents, as kotlinx.serialization types.
 *
 * These are hand-written rather than generated from the protobuf, and that is the whole reason this
 * module needs no OpenTelemetry SDK: OTLP/HTTP has a JSON encoding, the protobuf-to-JSON mapping is
 * specified, and a document is thirty lines of data classes. The SDK would bring its own `Context` on
 * a `ThreadLocal` — the one thing `stx-telemetry` exists to not have — plus a dependency tree to
 * carry it.
 *
 * Two rules from the proto3 JSON mapping decide most of what looks odd here:
 *
 * - **A 64-bit number is a string.** `timeUnixNano` and `intValue` are `fixed64`/`int64`, and JSON's
 *   number type cannot hold one without losing the low bits. Every OTLP receiver expects the quotes.
 * - **Bytes are hex, not base64, for ids.** The specification makes `traceId` and `spanId` an
 *   exception to the usual `bytes` encoding, which is convenient here: `stx-telemetry` already keeps
 *   them as hex.
 */
@Serializable
internal data class LogsDocument(
    val resourceLogs: List<ResourceLogs>,
)

@Serializable
internal data class ResourceLogs(
    val resource: OtlpResource,
    val scopeLogs: List<ScopeLogs>,
)

@Serializable
internal data class ScopeLogs(
    val scope: Scope,
    val logRecords: List<OtlpLogRecord>,
)

@Serializable
internal data class TracesDocument(
    val resourceSpans: List<ResourceSpans>,
)

@Serializable
internal data class ResourceSpans(
    val resource: OtlpResource,
    val scopeSpans: List<ScopeSpans>,
)

@Serializable
internal data class ScopeSpans(
    val scope: Scope,
    val spans: List<OtlpSpan>,
)

@Serializable
internal data class OtlpResource(
    val attributes: List<KeyValue>,
)

/** Who produced the signals. Constant here: it identifies the instrumentation, not the application. */
@Serializable
internal data class Scope(
    val name: String,
    val version: String? = null,
)

@Serializable
internal data class OtlpLogRecord(
    val timeUnixNano: String,
    val severityNumber: Int,
    val severityText: String,
    val body: AnyValue,
    val attributes: List<KeyValue> = emptyList(),
    val traceId: String? = null,
    val spanId: String? = null,
)

@Serializable
internal data class OtlpSpan(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String? = null,
    val name: String,
    val kind: Int,
    val startTimeUnixNano: String,
    val endTimeUnixNano: String,
    val attributes: List<KeyValue> = emptyList(),
    val events: List<OtlpEvent> = emptyList(),
    val status: OtlpStatus,
)

@Serializable
internal data class OtlpEvent(
    val timeUnixNano: String,
    val name: String,
    val attributes: List<KeyValue> = emptyList(),
)

/** Codes as OTLP numbers them: 0 unset, 1 ok, 2 error. */
@Serializable
internal data class OtlpStatus(
    val code: Int,
    val message: String? = null,
)

@Serializable
internal data class KeyValue(
    val key: String,
    val value: AnyValue,
)

/**
 * OTLP's tagged union for one value, and the reason an attribute has to be a scalar.
 *
 * Exactly one field is set. A backend indexes what it finds here, so a value with no case that fits
 * — a nested object — has nowhere to go, which is the constraint `stx-telemetry`'s "an attribute is
 * a scalar" rule exists to respect rather than to discover at the collector.
 */
@Serializable
internal data class AnyValue(
    val stringValue: String? = null,
    val boolValue: Boolean? = null,
    val intValue: String? = null,
    val doubleValue: Double? = null,
    val arrayValue: ArrayValue? = null,
)

@Serializable
internal data class ArrayValue(
    val values: List<AnyValue>,
)

/**
 * What a collector says when it took some of a batch and refused the rest.
 *
 * A 200 with a non-empty `partialSuccess` is the one answer that is neither success nor failure, and
 * the specification is explicit that it must not be retried: the accepted records would arrive
 * twice. It is reported instead — see `OtlpExporter`.
 */
@Serializable
data class PartialSuccess(
    val rejectedLogRecords: JsonPrimitive? = null,
    val rejectedSpans: JsonPrimitive? = null,
    val errorMessage: String = "",
) {
    /**
     * How many records the collector refused, however it chose to write the number.
     *
     * The counts are `int64`, which the proto3 JSON mapping writes as a **string** — but a receiver
     * is allowed to accept either, and not every implementation emits the quotes. Reading the
     * primitive's content rather than declaring a `Long` accepts both without a custom serializer.
     */
    val rejected: Long
        get() = (rejectedLogRecords?.count() ?: 0) + (rejectedSpans?.count() ?: 0)

    val isEmpty: Boolean get() = rejected == 0L && errorMessage.isEmpty()
}

private fun JsonPrimitive.count(): Long = content.toLongOrNull() ?: 0

@Serializable
internal data class ExportResponse(
    val partialSuccess: PartialSuccess? = null,
)
