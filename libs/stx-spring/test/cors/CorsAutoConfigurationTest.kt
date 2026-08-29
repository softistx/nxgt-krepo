package com.strange.spring.cors

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.cors.reactive.CorsWebFilter

class CorsAutoConfigurationTest :
    StringSpec({
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CorsAutoConfiguration::class.java))

        "no filter until an application asks for one" {
            runner.run { context -> context.getBeanNamesForType(CorsWebFilter::class.java).size shouldBe 0 }
        }

        "stx.cors.enabled registers the filter" {
            runner
                .withPropertyValues("stx.cors.enabled=true", "stx.cors.origins=http://localhost:5173")
                .run { context -> context.getBeanNamesForType(CorsWebFilter::class.java).size shouldBe 1 }
        }

        "a wildcard origin with credentials fails at startup, not at the first preflight" {
            // The combination the CORS specification forbids. Spring throws when the request
            // arrives, which turns a configuration mistake into an intermittent browser failure
            // found by whoever is testing the front end. This fails while the context is starting
            // and says what to use instead.
            runner
                .withPropertyValues(
                    "stx.cors.enabled=true",
                    "stx.cors.origins=*",
                    "stx.cors.allow-credentials=true",
                ).run { context ->
                    context.startupFailure shouldNotBe null
                    context.startupFailure!!.stackTraceToString() shouldContain "stx.cors.origin-patterns"
                }
        }

        "a wildcard origin without credentials is allowed" {
            runner
                .withPropertyValues(
                    "stx.cors.enabled=true",
                    "stx.cors.origins=*",
                    "stx.cors.allow-credentials=false",
                ).run { context -> context.startupFailure shouldBe null }
        }

        "origin patterns are how a wildcard and credentials go together" {
            runner
                .withPropertyValues(
                    "stx.cors.enabled=true",
                    "stx.cors.origin-patterns=https://*.example.com",
                    "stx.cors.allow-credentials=true",
                ).run { context ->
                    context.startupFailure shouldBe null
                    context.getBeanNamesForType(CorsWebFilter::class.java).size shouldBe 1
                }
        }
    })
