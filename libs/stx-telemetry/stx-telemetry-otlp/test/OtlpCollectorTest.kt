package com.strange.telemetry.otlp

import com.strange.telemetry.Telemetry
import com.strange.telemetry.context.withTelemetry
import com.strange.telemetry.logger
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.span
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable
@SerialName("checkout.charged")
private data class Charged(
    val orderId: String,
    val amount: Long,
)

/**
 * The one spec that talks to a real collector, and therefore the one that is off by default.
 *
 * `OtlpExporterTest` proves the *document* against a JDK HTTP server, deterministically and in
 * milliseconds. What it cannot prove is that a real collector agrees with our reading of the JSON
 * mapping — that an `int64` really has to be quoted, that a hex trace id is really accepted where
 * the protobuf says bytes. Only a collector can answer that, so this asks one when there is one:
 *
 * ```bash
 * OTLP_TEST_ENDPOINT=http://localhost:4318 ./kotlin test -m stx-telemetry-otlp
 * ```
 *
 * A collector answers `200` to almost anything, so the assertion is not the status: it is that
 * `partialSuccess` comes back **empty**, which is where a collector says it threw part of the
 * document away.
 */
class OtlpCollectorTest :
    FeatureSpec({
        val endpoint: String? = System.getenv("OTLP_TEST_ENDPOINT")
        val log = logger("otlp-collector-spec")

        feature("against a real collector").config(enabled = endpoint != null) {
            scenario("it takes a trace and its logs without rejecting any of it") {
                val rejected = mutableListOf<PartialSuccess>()
                val failures = mutableListOf<Throwable>()
                val telemetry =
                    Telemetry("stx-telemetry-spec") {
                        version = "0.1.0"
                        environment = "test"
                        linger = 10.milliseconds
                        onExportError = { failures += it }
                        export(OtlpExporter(endpoint!!, onPartialSuccess = { rejected += it }))
                    }

                withTelemetry(telemetry) {
                    span("charge", "orderId" to "A-91", kind = SpanKind.Client) {
                        log.info(Charged("A-91", 4999))
                        event("retrying", "attempt" to 2)
                        attribute("processor", "probe")
                    }
                }
                telemetry.close()

                failures.map { it.message } shouldBe emptyList()
                rejected shouldBe emptyList()
            }
        }
    })
