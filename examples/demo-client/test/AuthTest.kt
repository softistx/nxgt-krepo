package com.softistx.demo.client

import com.softistx.demo.api.startDemoServer
import com.softistx.demo.client.api.utils.BasicCredentials
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
                DemoClient(anonymous.baseUrl, token = { current }).use { client ->
                    client.session.whoAmI().subject shouldBe "first"
                    current = "second"
                    client.session.whoAmI().subject shouldBe "second"
                }
            }
        }

        // `security` is a list of alternatives: any one is enough, and the schemes inside one are all
        // needed. Flattened, `/session/either` sent every credential the client held.
        feature("an operation whose security lists a bearer token or basic credentials") {
            val base64 = java.util.Base64.getEncoder()

            fun client(
                token: String?,
                basic: BasicCredentials?,
            ) = DemoClient(authorized.baseUrl, token = token?.let { t -> suspend { t } }, basic = basic?.let { b -> suspend { b } })

            scenario("a client holding the first alternative sends it") {
                client("t0k", null).use { it.session.echoEither().authorization shouldBe "Bearer t0k" }
            }

            scenario("a client holding only the second sends that one") {
                client(null, BasicCredentials("ada", "pw")).use {
                    it.session.echoEither().authorization shouldBe "Basic " + base64.encodeToString("ada:pw".toByteArray())
                }
            }

            scenario("a client holding both sends the first alternative only, never both") {
                client("t0k", BasicCredentials("ada", "pw")).use {
                    it.session.echoEither().authorization shouldBe "Bearer t0k"
                }
            }

            scenario("a client holding neither fails before sending, naming the alternatives") {
                client(null, null).use {
                    val thrown = shouldThrow<IllegalStateException> { it.session.echoEither() }
                    thrown.message shouldContain "echoEither"
                    thrown.message shouldContain "[Bearer] or [Basic]"
                }
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
