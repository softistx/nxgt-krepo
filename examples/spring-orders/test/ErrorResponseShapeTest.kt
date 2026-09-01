package com.softistx.example.orders

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import com.softistx.example.orders.api.models.ErrorResponse as DocumentedError
import com.softistx.spring.error.ErrorResponse as ActualError

/**
 * That the document's `ErrorResponse` and the one this service actually writes are the same shape.
 *
 * Nothing else checks it. Every error response here is built by `ApiExceptionHandler` from
 * `com.softistx.spring.error.ErrorResponse`, which belongs to `stx-spring-boot` and knows nothing
 * about this application's document — so the document's account of a failure is a claim, and a
 * claim about a wire format is exactly the kind that is quietly wrong for a year.
 *
 * The direction matters: what the server writes must be readable as what the document promises.
 * The reverse would pass with a document that describes less than the server sends.
 */
class ErrorResponseShapeTest :
    FeatureSpec({
        val json =
            Json {
                ignoreUnknownKeys = false
                explicitNulls = false
            }

        feature("what the server writes is what the document describes") {
            scenario("a fully populated failure reads back field for field") {
                val actual =
                    ActualError(
                        message = "No order with id 1.",
                        status = "NOT_FOUND",
                        code = "orders.not-found",
                        timestamp = "2026-01-01T00:00:00Z",
                        debugMessage = "findById returned nothing",
                    )

                // `ignoreUnknownKeys = false` is the assertion: a field the server sends and the
                // document never mentions fails here rather than reaching a client as a surprise.
                val documented = json.decodeFromString<DocumentedError>(json.encodeToString(actual))

                documented.message shouldBe actual.message
                documented.status shouldBe actual.status
                documented.code shouldBe actual.code
                documented.timestamp shouldBe actual.timestamp
                documented.debugMessage shouldBe actual.debugMessage
            }

            scenario("the optional halves are optional on both sides") {
                // `stx.errors.include-debug-message` off, and a failure raised without a key — the
                // ordinary shape in a deployment, and the one a client is most likely to meet.
                val actual = ActualError(message = "Something went wrong.", status = "INTERNAL_SERVER_ERROR")

                val documented = json.decodeFromString<DocumentedError>(json.encodeToString(actual))

                documented.code shouldBe null
                documented.debugMessage shouldBe null
                documented.timestamp shouldBe actual.timestamp
            }
        }
    })
