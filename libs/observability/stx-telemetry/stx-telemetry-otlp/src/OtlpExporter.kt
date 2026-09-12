package com.softistx.telemetry.otlp

import com.softistx.common.lifecycle.CloseGuard
import com.softistx.telemetry.export.Exporter
import com.softistx.telemetry.model.LogRecord
import com.softistx.telemetry.model.Resource
import com.softistx.telemetry.model.Signal
import com.softistx.telemetry.model.SpanRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.GZIPOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Ships signals to an OTLP collector over HTTP, in JSON.
 *
 * ```kotlin
 * Telemetry("checkout") {
 *     export(OtlpExporter("http://localhost:4318"))
 * }.install()
 * ```
 *
 * [endpoint] is the collector's base URL; the two signal paths — `/v1/logs` and `/v1/traces` — are
 * appended. A batch that holds both goes out as two requests, which is what the protocol asks for.
 *
 * ## Why there is no OpenTelemetry SDK here
 *
 * OTLP/HTTP has a JSON encoding and the protobuf-to-JSON mapping is specified, so a document is
 * thirty lines of `@Serializable` data classes — see `Document.kt`. The Java SDK would bring its own
 * `Context` on a `ThreadLocal`, which is the single thing `stx-telemetry` exists to not have, plus a
 * dependency tree to carry it. This module's only dependency is the core one.
 *
 * ## What it retries, and what it deliberately does not
 *
 * A connection failure and a 408/429/5xx are retried [attempts] times with a doubling [backoff]. A
 * 4xx that is not one of those is the collector saying the document is wrong, and sending it again
 * would only be wrong again.
 *
 * A **200 with a non-empty `partialSuccess`** is neither: the collector took some records and refused
 * others. The specification is explicit that it must not be retried — the accepted records would
 * arrive twice — so it is reported through [onPartialSuccess] and the batch is done. The default
 * hands it to `onExportError` by throwing, which is where the pipeline's own failure reporting is.
 */
class OtlpExporter(
    endpoint: String,
    /** Sent on every request: an API key, a tenant header, whatever the collector in front asks for. */
    private val headers: Map<String, String> = emptyMap(),
    private val timeout: Duration = 10.seconds,
    /** How many times one document is sent before giving up. 1 disables retrying. */
    private val attempts: Int = 3,
    /** The first wait between attempts; it doubles each time. */
    private val backoff: Duration = 500.milliseconds,
    /** Compresses the body. Every OTLP/HTTP receiver is required to understand it. */
    private val gzip: Boolean = true,
    private val onPartialSuccess: (PartialSuccess) -> Unit = { throw OtlpRejectedException(it) },
    /**
     * A client to use instead of one of this exporter's own.
     *
     * Passing one means **this exporter will not close it**, on the rule the rest of the repository
     * follows: close only what you opened. An application that already has a tuned `HttpClient` — a
     * proxy, a truststore, a connection budget — should not have a second one appear because it
     * turned telemetry on.
     */
    client: HttpClient? = null,
) : Exporter {
    private val logsUrl = URI.create(endpoint.trimEnd('/') + "/v1/logs")
    private val tracesUrl = URI.create(endpoint.trimEnd('/') + "/v1/traces")

    private val owned = client == null
    private val http =
        client ?: HttpClient
            .newBuilder()
            .connectTimeout(timeout.toJavaDuration())
            .build()

    private val guard = CloseGuard()

    override suspend fun export(
        resource: Resource,
        batch: List<Signal>,
    ) {
        val logs = batch.filterIsInstance<LogRecord>()
        val spans = batch.filterIsInstance<SpanRecord>()
        if (logs.isNotEmpty()) post(logsUrl, LogsDocument.serializer(), logsDocument(resource, logs))
        if (spans.isNotEmpty()) post(tracesUrl, TracesDocument.serializer(), tracesDocument(resource, spans))
    }

    private suspend fun <T> post(
        url: URI,
        serializer: KSerializer<T>,
        document: T,
    ) {
        val body = json.encodeToString(serializer, document).toByteArray()
        val payload = if (gzip) body.gzipped() else body
        var wait = backoff

        repeat(attempts) { attempt ->
            val last = attempt == attempts - 1
            val response =
                try {
                    send(url, payload)
                } catch (failure: IOException) {
                    if (last) throw OtlpUnreachableException(url, failure)
                    null
                }

            if (response != null) {
                val status = response.statusCode()
                if (status in 200..299) return accept(response.body())
                if (status !in RETRYABLE) throw OtlpRefusedException(url, status, response.body())
                if (last) throw OtlpRefusedException(url, status, response.body())
            }

            delay(wait)
            wait *= 2
        }
    }

    private suspend fun send(
        url: URI,
        payload: ByteArray,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(url)
                .timeout(timeout.toJavaDuration())
                .header("Content-Type", "application/json")
                .apply {
                    if (gzip) header("Content-Encoding", "gzip")
                    headers.forEach { (name, value) -> header(name, value) }
                }.POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build()
        // sendAsync rather than send: this runs on the pipeline's single consumer, and a blocking
        // call there would stop every other exporter for the length of one network round trip.
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
    }

    private fun accept(body: String) {
        if (body.isBlank()) return
        val partial =
            try {
                json.decodeFromString(ExportResponse.serializer(), body).partialSuccess
            } catch (_: Exception) {
                // A 200 whose body we cannot read is still a 200. The records are in.
                null
            }
        if (partial != null && !partial.isEmpty) onPartialSuccess(partial)
    }

    override fun close() =
        guard.once {
            if (owned) http.close()
        }

    private companion object {
        /** The statuses the specification names as worth sending again. */
        val RETRYABLE = setOf(408, 429, 500, 502, 503, 504)

        val json =
            Json {
                // OTLP's fields are optional and a receiver reads absence as absence; writing nulls
                // would put `"parentSpanId": null` on every root span in the fleet.
                explicitNulls = false
                encodeDefaults = false
            }
    }
}

private fun ByteArray.gzipped(): ByteArray =
    ByteArrayOutputStream(size / 2).use { bytes ->
        GZIPOutputStream(bytes).use { it.write(this) }
        bytes.toByteArray()
    }
