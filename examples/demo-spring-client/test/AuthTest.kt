package com.softistx.demo.spring

import com.softistx.demo.api.DemoServer
import com.softistx.demo.api.startDemoServer
import com.softistx.demo.spring.api.utils.BasicCredentials
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The document says which operations need a credential and which do not — a root
 * `security: - Bearer: []` with `security: []` on the ones that hand the token out — and until now
 * none of that reached the client. A hand-written interceptor has to send the header everywhere,
 * including to the sign-in endpoint, or keep its own copy of the list.
 *
 * The half that is easy to get wrong is the second one, and the only way to see it is to ask the
 * server what it received: a header that is correctly absent leaves no other trace.
 *
 * Spring reaches it differently — the annotation is read off the reflective method by a
 * `HttpRequestValues.Processor` and travels to the filter as a request attribute — so the same
 * assertions have to be made twice for the two clients to stay substitutable.
 */
class AuthTest :
    FeatureSpec({
        lateinit var server: DemoServer
        lateinit var authorized: SpringDemoClient
        lateinit var anonymous: SpringDemoClient

        beforeSpec {
            server = startDemoServer(port = 0)
            authorized = SpringDemoClient(server.baseUrl, token = { "s3cret" })
            anonymous = SpringDemoClient(server.baseUrl)
        }

        afterSpec { server.close() }

        feature("an operation that inherits the document's root security") {

            scenario("the credential is attached, and the server sees it") {
                authorized.session.whoAmI().subject shouldBe "s3cret"
            }

            scenario("without one the call fails before it is sent, naming what was expected") {
                // No alternative is configured and the document offers no anonymous one, so the client
                // refuses rather than send a request it knows the server will answer with a 401.
                val thrown = shouldThrow<IllegalStateException> { anonymous.session.whoAmI() }
                thrown.message shouldContain "whoAmI"
                thrown.message shouldContain "[Bearer]"
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
                val client = SpringDemoClient(server.baseUrl, token = { current })
                client.session.whoAmI().subject shouldBe "first"
                current = "second"
                client.session.whoAmI().subject shouldBe "second"
            }
        }

        // The same alternatives the Ktorfit client's AuthTest asks about: any one is enough, and a
        // client sends the first it holds a credential for and never two.
        feature("an operation whose security lists a bearer token or basic credentials") {
            val base64 = java.util.Base64.getEncoder()

            fun client(
                token: String?,
                basic: BasicCredentials?,
            ) = SpringDemoClient(
                server.baseUrl,
                token = token?.let { t -> suspend { t } },
                basic = basic?.let { b -> suspend { b } },
            )

            scenario("a client holding the first alternative sends it") {
                client("t0k", null).session.echoEither().authorization shouldBe "Bearer t0k"
            }

            scenario("a client holding only the second sends that one") {
                client(null, BasicCredentials("ada", "pw")).session.echoEither().authorization shouldBe
                    "Basic " + base64.encodeToString("ada:pw".toByteArray())
            }

            scenario("a client holding both sends the first alternative only, never both") {
                client("t0k", BasicCredentials("ada", "pw")).session.echoEither().authorization shouldBe "Bearer t0k"
            }

            scenario("a client holding neither fails before sending, naming the alternatives") {
                val thrown = shouldThrow<IllegalStateException> { client(null, null).session.echoEither() }
                thrown.message shouldContain "echoEither"
                thrown.message shouldContain "[Bearer] or [Basic]"
            }
        }

        feature("an operation whose security lists a bearer token or {}") {

            scenario("a client holding a token sends it") {
                authorized.session.echoOptional().authorization shouldBe "Bearer s3cret"
            }

            scenario("a client holding none sends nothing, instead of failing") {
                anonymous.session.echoOptional().authorization shouldBe null
            }
        }
    })
