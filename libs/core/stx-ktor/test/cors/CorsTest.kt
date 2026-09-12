package com.softistx.ktor.cors

import com.softistx.common.http.CorsPolicy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

/**
 * The plugin against a real server, because what a browser is told is a header on a real response
 * and not a value in a builder.
 */
class CorsTest :
    FeatureSpec({

        fun ApplicationTestBuilder.serve(policy: CorsPolicy) {
            application {
                cors(policy)
                routing { get("/orders") { call.respondText("[]") } }
            }
        }

        feature("a policy reaches the wire") {
            scenario("an allowed origin is echoed back") {
                testApplication {
                    serve(CorsPolicy(origins = listOf("http://localhost:5173")))

                    val response =
                        client.get("/orders") { header(HttpHeaders.Origin, "http://localhost:5173") }

                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBe "http://localhost:5173"
                }
            }

            scenario("an origin nobody allowed gets nothing") {
                testApplication {
                    serve(CorsPolicy(origins = listOf("http://localhost:5173")))

                    val response = client.get("/orders") { header(HttpHeaders.Origin, "http://evil.test") }

                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBe null
                }
            }

            scenario("a preflight is answered with the methods the policy names") {
                testApplication {
                    serve(
                        CorsPolicy(
                            origins = listOf("http://localhost:5173"),
                            methods = listOf("GET", "POST"),
                            allowCredentials = false,
                        ),
                    )

                    val response =
                        client.options("/orders") {
                            header(HttpHeaders.Origin, "http://localhost:5173")
                            header(HttpHeaders.AccessControlRequestMethod, "POST")
                        }

                    response.status shouldBe HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowMethods]!!.contains("POST") shouldBe true
                }
            }

            scenario("credentials are advertised when the policy allows them") {
                testApplication {
                    serve(CorsPolicy(origins = listOf("http://localhost:5173"), allowCredentials = true))

                    val response = client.get("/orders") { header(HttpHeaders.Origin, "http://localhost:5173") }

                    response.headers[HttpHeaders.AccessControlAllowCredentials] shouldBe "true"
                }
            }
        }

        feature("the policy is checked before the server starts serving") {
            scenario("a wildcard origin with credentials fails at install") {
                // The same rule stx-spring-boot enforces, from the same code — this is the whole
                // point of the policy being a shared type rather than two configuration classes
                // that agree for as long as somebody keeps them agreeing.
                shouldThrow<IllegalArgumentException> {
                    testApplication {
                        serve(CorsPolicy(origins = listOf("*"), allowCredentials = true))
                        client.get("/orders")
                    }
                }
            }
        }

        feature("what Ktor cannot express is named rather than dropped") {
            scenario("origin patterns and a path are reported as ignored") {
                // A configuration field a framework quietly ignores is worse than one it refuses:
                // nothing fails, and the policy in the file is not the policy in force.
                CorsPolicy(originPatterns = listOf("https://sub.example.com"), path = "/api/**")
                    .ignoredByKtor shouldBe listOf("originPatterns", "path")
            }

            scenario("a policy Ktor can express fully reports nothing") {
                CorsPolicy(origins = listOf("http://localhost:5173")).ignoredByKtor shouldBe emptyList()
            }
        }
    })
