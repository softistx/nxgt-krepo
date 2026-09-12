package com.softistx.ktor.di

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.testing.testApplication

/**
 * What the integrations' `provideX` functions rest on, checked rather than assumed.
 *
 * Two claims come from Ktor's documentation and decide the design: that `dependencies { }` needs no
 * `install` of its own, and that Ktor's DI closes every `AutoCloseable` it created when the
 * application stops. The second is why the plugins distinguish what they opened from what they were
 * handed — if it were false, adopting would be the only safe mode, and if it is true, closing an
 * adopted resource is a double close.
 *
 * Only [Probe] here, and that is the point: these are facts about Ktor's container, not about any
 * one integration, so they belong beside the idiom rather than beside a backend. The other half —
 * a plugin actually handing the container what it opened — is in `stx-redis-ktor`'s own specs,
 * beside the plugin it is about.
 */
class DependenciesTest :
    FeatureSpec({

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
