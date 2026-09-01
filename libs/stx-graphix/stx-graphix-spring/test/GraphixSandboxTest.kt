package com.softistx.graphix.spring

import com.softistx.graphix.http.apolloSandboxPage
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

/** The sandbox router on its own, the way GraphixHttpTest tests the JSON handler. */
class GraphixSandboxTest :
    FeatureSpec({
        feature("GET /sandbox") {
            scenario("answers HTML carrying Apollo's embeddable build") {
                val client =
                    WebTestClient
                        .bindToRouterFunction(sandboxRouter("/sandbox", apolloSandboxPage("/graphql")))
                        .build()

                client
                    .get()
                    .uri("/sandbox")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.TEXT_HTML)
                    .expectBody<String>()
                    .value { body ->
                        body shouldContain "embeddable-sandbox.cdn.apollographql.com"
                        body shouldContain """new URL("/graphql", window.location.origin)"""
                    }
            }
        }
    })
