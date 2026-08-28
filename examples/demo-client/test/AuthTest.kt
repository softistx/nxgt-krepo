package com.strange.demo.client

import com.strange.demo.api.startDemoServer
import com.strange.demo.client.api.utils.ErrorResponseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The document says which operations need a credential and which do not — a root
 * `security: - Bearer: []` with `security: []` on the ones that hand the token out — and until now
 * none of that reached the client. A hand-written interceptor has to send the header everywhere,
 * including to the sign-in endpoint, or keep its own copy of the list.
 *
 * The half that is easy to get wrong is the second one, and the only way to see it is to ask the
 * server what it received: a header that is correctly absent leaves no other trace.
 */
class AuthTest :
    FeatureSpec({
        lateinit var server: AutoCloseable
        lateinit var authorized: DemoClient
        lateinit var anonymous: DemoClient

        beforeSpec {
            val started = startDemoServer(port = 0)
            server = started
            authorized = DemoClient(started.baseUrl, token = { "s3cret" })
            anonymous = DemoClient(started.baseUrl)
        }

        afterSpec {
            authorized.close()
            anonymous.close()
            server.close()
        }

        feature("an operation that inherits the document's root security") {

            scenario("the credential is attached, and the server sees it") {
                authorized.session.whoAmI().subject shouldBe "s3cret"
            }

            scenario("without one the call fails as the document says it will") {
                val thrown = shouldThrow<ErrorResponseException> { anonymous.session.whoAmI() }
                thrown.status shouldBe 401
                thrown.error.message shouldBe "no bearer token"
            }
        }

        feature("an operation that overrides the root with security: []") {

            scenario("nothing is attached, even by a client that holds a token") {
                authorized.session.echoSession().authorization shouldBe null
            }

            scenario("and it works for a client that holds none") {
                anonymous.session.echoSession().authorization shouldBe null
            }
        }

        feature("a token that changes") {

            scenario("the slot is read per request, not once at construction") {
                var current = "first"
                DemoClient(anonymous.baseUrl, token = { current }).use { client ->
                    client.session.whoAmI().subject shouldBe "first"
                    current = "second"
                    client.session.whoAmI().subject shouldBe "second"
                }
            }
        }
    })
