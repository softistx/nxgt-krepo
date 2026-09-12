package com.softistx.telemetry.otlp

import java.net.URI

/** What this module throws. Every one of them reaches the pipeline's `onExportError` and no further. */
sealed class OtlpException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** The collector could not be reached, after every attempt. */
class OtlpUnreachableException internal constructor(
    val url: URI,
    cause: Throwable,
) : OtlpException("could not reach the OTLP collector at $url", cause)

/**
 * The collector answered, and said no.
 *
 * The body is included because a collector's refusal names the field it did not like, and that is
 * the whole of the diagnosis — this is a document somebody's code generated, not user input.
 */
class OtlpRefusedException internal constructor(
    val url: URI,
    val status: Int,
    val body: String,
) : OtlpException("the OTLP collector at $url answered $status: ${body.take(500)}")

/**
 * The collector took part of the batch and refused the rest.
 *
 * It is not retried — the accepted records would arrive twice — so this exists to make the loss
 * visible rather than to trigger anything. Pass `onPartialSuccess` to handle it some other way.
 */
class OtlpRejectedException internal constructor(
    val partialSuccess: PartialSuccess,
) : OtlpException(
        "the OTLP collector rejected ${partialSuccess.rejected} records: ${partialSuccess.errorMessage}",
    )
