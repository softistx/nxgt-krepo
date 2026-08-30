package com.strange.spring.error

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.ObjectMapper
import kotlin.time.Instant

/**
 * The one thing about [ErrorResponse] a client actually depends on: that the body says the same
 * thing whichever codec produced it.
 *
 * This spec exists because it did not. `timestamp` was a `kotlin.time.Instant`, and Jackson — the
 * codec WebFlux uses until an application replaces it — wrote it as
 * `{"epochSeconds":1788027656,"nanosecondsOfSecond":594940311}` while kotlinx wrote
 * `"2026-08-29T18:21:34.686320906Z"`. Both serialized happily; only a client trying to read a
 * timestamp would ever have found out.
 */
class ErrorResponseWireTest :
    StringSpec({
        val body =
            ErrorResponse(
                message = "No order 7",
                status = "NOT_FOUND",
                code = "orders.not-found",
                timestamp = "2026-08-29T18:21:34.686320906Z",
            )

        "kotlinx writes the timestamp as the ISO-8601 text it is" {
            Json.encodeToString(body) shouldContain "\"timestamp\":\"2026-08-29T18:21:34.686320906Z\""
        }

        "Jackson writes exactly the same thing" {
            // The default codec for WebFlux, built the way Spring Boot builds it.
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
                .run { context ->
                    context.getBean(ObjectMapper::class.java).writeValueAsString(body) shouldContain
                        "\"timestamp\":\"2026-08-29T18:21:34.686320906Z\""
                }
        }

        "the default timestamp is a value, not just text" {
            // A String on the wire is only acceptable while it is still parseable back.
            Instant.parse(ErrorResponse(message = "x", status = "BAD_REQUEST").timestamp)
            ErrorResponse(message = "x", status = "BAD_REQUEST", timestamp = body.timestamp).instant shouldBe
                Instant.parse("2026-08-29T18:21:34.686320906Z")
        }
    })
