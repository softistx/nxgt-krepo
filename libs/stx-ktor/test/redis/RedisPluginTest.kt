package com.strange.ktor.redis

import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import com.strange.testing.containers.redisContainer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/**
 * The plugin against a real server, because what it promises — one connection, closed on stop — is
 * not observable from a mock.
 */
class RedisPluginTest :
    FeatureSpec({

        val server = redisContainer()

        feature("a route reaching for Redis").config(enabled = server.available) {
            scenario("gets a working connection, namespaced as configured") {
                testApplication {
                    application {
                        install(RedisConnection) { config = RedisConfig(uri = server.endpoint!!, namespace = "orders") }
                        routing {
                            get("/") {
                                call.redis.commands.set(call.redis.key("greeting"), "hello")
                                call.respondText(
                                    "${call.redis.commands.get(call.redis.key("greeting"))}:${call.redis.namespace}",
                                )
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "hello:orders"
                }
            }

            scenario("it is one connection, not one per request") {
                testApplication {
                    application {
                        install(RedisConnection) { config = RedisConfig(uri = server.endpoint!!) }
                        routing { get("/") { call.respondText("${System.identityHashCode(call.redis)}") } }
                    }
                    val first = client.get("/").bodyAsText()

                    client.get("/").bodyAsText() shouldBe first
                }
            }

            scenario("and it is closed when the application stops") {
                lateinit var captured: Redis
                testApplication {
                    application {
                        install(RedisConnection) { config = RedisConfig(uri = server.endpoint!!) }
                        routing {
                            get("/") {
                                captured = call.redis
                                call.respondText("ok")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "ok"
                }

                // Leaking a pool per redeploy is invisible until a server runs out of handles, so
                // this is the assertion the plugin exists for.
                shouldThrowAny { captured.ping() }
            }
        }

        feature("a connection handed in rather than opened").config(enabled = server.available) {
            scenario("is the one routes get, and is still open after the application stops") {
                val mine = Redis.connect(RedisConfig(uri = server.endpoint!!, namespace = "adopted"))
                try {
                    lateinit var captured: Redis
                    testApplication {
                        application {
                            install(RedisConnection) { instance = mine }
                            routing {
                                get("/") {
                                    captured = call.redis
                                    call.respondText(call.redis.namespace)
                                }
                            }
                        }
                        client.get("/").bodyAsText() shouldBe "adopted"
                    }

                    captured shouldBeSameInstanceAs mine

                    // The whole point of the split between `own` and `publish`, and invisible any
                    // other way: a DI container closes what it built, so a plugin that closed this
                    // one too would be the second close.
                    mine.ping()
                } finally {
                    mine.close()
                }
            }
        }

        feature("reaching for it without installing it") {
            scenario("names the plugin rather than failing three layers down") {
                // Asserted on the accessor rather than through the client: Ktor's test engine turns
                // a handler exception into a 500 page, so going over HTTP would prove only that
                // something went wrong, not that the message says which install is missing.
                testApplication {
                    application {
                        val failure = shouldThrow<IllegalStateException> { redis }

                        failure.message shouldContain "RedisConnection"
                    }

                    startApplication()
                }
            }
        }
    })
