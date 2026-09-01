package com.softistx.ktor.di

import com.softistx.ktor.redis.RedisConnection
import com.softistx.ktor.redis.redis
import com.softistx.redis.Redis
import com.softistx.redis.RedisConfig
import com.softistx.testing.containers.redisContainer
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/**
 * What this module's `provideX` functions rest on, checked rather than assumed.
 *
 * Two claims come from Ktor's documentation and decide the design: that `dependencies { }` needs no
 * `install` of its own, and that Ktor's DI closes every `AutoCloseable` it created when the
 * application stops. The second is why the plugins distinguish what they opened from what they were
 * handed — if it were false, adopting would be the only safe mode, and if it is true, closing an
 * adopted resource is a double close.
 */
class DependenciesTest :
    FeatureSpec({

        val server = redisContainer()

        feature("Ktor's own dependency injection") {
            scenario("resolves what was registered, with no plugin installed for it") {
                testApplication {
                    application {
                        dependencies { provide<Probe> { Probe() } }

                        dependencies.resolve<Probe>().name shouldBe "probe"
                    }

                    startApplication()
                }
            }

            scenario("hands out one instance, not one per resolution") {
                testApplication {
                    application {
                        dependencies { provide<Probe> { Probe() } }

                        dependencies.resolve<Probe>() shouldBeSameInstanceAs dependencies.resolve<Probe>()
                    }

                    startApplication()
                }
            }

            scenario("closes an AutoCloseable it created when the application stops") {
                lateinit var probe: Probe
                testApplication {
                    application {
                        dependencies { provide<Probe> { Probe() } }

                        probe = dependencies.resolve()
                        probe.closed shouldBe false
                    }

                    startApplication()
                }

                // The fact the ownership split exists for: the container closes what it built, so a
                // plugin handed one of these must not close it too.
                probe.closed shouldBe true
            }
        }

        feature("a dependency the container did not build") {
            scenario("is closed by the container all the same, and a per-key cleanup does not stop it") {
                val built = Probe()
                val spared = Probe()
                testApplication {
                    application {
                        dependencies {
                            provide<Probe> { built }
                            key<Probe>("spared") {
                                provide { spared }
                                cleanup { }
                            }
                        }

                        dependencies.resolve<Probe>() shouldBeSameInstanceAs built
                        // Resolved, and not only registered: nothing is instantiated until it is
                        // asked for, so an unresolved dependency proves nothing about cleanup.
                        dependencies.resolve<Probe>("spared") shouldBeSameInstanceAs spared
                    }

                    startApplication()
                }

                // Both, and this is the fact the design had to be built around: closing is a
                // global `onShutdown` on the DI configuration — `(value as? AutoCloseable)?.close()`
                // — and a per-key `cleanup` is a second hook that runs beside it, not instead of
                // it. Nothing a library registers can opt out; only the application can, by
                // replacing `onShutdown` in `install(DI)`.
                built.closed shouldBe true
                spared.closed shouldBe true
            }
        }

        feature("a plugin asked to make its resource injectable").config(enabled = server.available) {
            scenario("hands the container the one it opened rather than a second connection") {
                lateinit var injected: Redis
                testApplication {
                    application {
                        install(RedisConnection) {
                            config = RedisConfig(uri = server.requireEndpoint(), namespace = "di")
                            injectable = true
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
                    application {
                        install(RedisConnection) {
                            config = RedisConfig(uri = server.requireEndpoint(), namespace = "twice")
                            injectable = true
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

/** Something closeable that owes nothing to a backend, so the two claims above can be checked without one. */
private class Probe : AutoCloseable {
    val name = "probe"
    var closed = false
        private set

    override fun close() {
        closed = true
    }
}
