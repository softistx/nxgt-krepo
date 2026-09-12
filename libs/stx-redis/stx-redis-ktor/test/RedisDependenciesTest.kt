package com.softistx.redis.ktor

import com.softistx.redis.Redis
import com.softistx.redis.RedisConfig
import com.softistx.testing.containers.redisContainer
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/**
 * What installing the plugin registers with Ktor's DI, against a real server.
 *
 * What Ktor's container does on its own — that `dependencies { }` needs no `install`, and that it
 * closes every `AutoCloseable` it hands out — is checked without a backend in `stx-ktor`'s own
 * `DependenciesTest`. This is the half that needs one: that the plugin hands the container the
 * connection it opened rather than letting it build a second, and that both of them closing it is
 * harmless.
 */
class RedisDependenciesTest :
    FeatureSpec({

        val server = redisContainer()

        // Ktor abandons module loading after `ktor.application.startupTimeoutMillis` — ten seconds
        // unless set (ktor-server-core 3.5.2, `getStartupTimeout`) — and the plugin connects to
        // Redis while the module loads. On a CI runner shared with every other module's test JVMs
        // that took longer than ten seconds, in two runs out of two. The limit proves nothing about
        // the plugin, so it is a minute here. It has to be set in code: `testApplication` does not
        // read an `application.conf` from the test resources, which was tried.
        val slowStart = MapApplicationConfig("ktor.application.startupTimeoutMillis" to "60000")

        feature("a plugin registering its resource with the container").config(enabled = server.available) {
            scenario("hands the container the one it opened rather than a second connection") {
                lateinit var injected: Redis
                testApplication {
                    environment { config = slowStart }
                    application {
                        install(RedisConnection) {
                            config = RedisConfig(uri = server.requireEndpoint(), namespace = "di")
                        }

                        injected = dependencies.resolve()

                        routing {
                            get("/") {
                                call.redis.commands.set(call.redis.key("k"), "v")
                                call.respondText(
                                    "${call.redis.commands.get(call.redis.key("k"))}:" +
                                        "${call.redis === injected}",
                                )
                            }
                        }
                    }

                    // One connection, reachable both ways — not one for routes and one for the
                    // container, which is what a provider that opened its own would have given.
                    client.get("/").bodyAsText() shouldBe "v:true"
                }

                shouldThrowAny { injected.ping() }
            }

            scenario("and both of them closing it is harmless") {
                // The plugin closes what it opened and the container closes what it hands out, so
                // this connection is closed twice. It survives that because `Redis.close` is
                // guarded — the reason `CloseGuard` exists.
                lateinit var injected: Redis
                testApplication {
                    environment { config = slowStart }
                    application {
                        install(RedisConnection) {
                            config = RedisConfig(uri = server.requireEndpoint(), namespace = "twice")
                        }

                        injected = dependencies.resolve()

                        routing { get("/") { call.respondText(call.redis.namespace) } }
                    }

                    client.get("/").bodyAsText() shouldBe "twice"
                }

                injected.close()
                shouldThrowAny { injected.ping() }
            }
        }
    })
