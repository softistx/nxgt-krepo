package com.softistx.testing.containers

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * How a service decides where it is.
 *
 * The resolution order is the whole point of the type, so it is tested without Docker wherever it
 * can be: an override that wins, and a failure that is a skip rather than a crash. Only the last
 * feature actually starts something.
 */
class ContainerServiceTest :
    FeatureSpec({

        feature("an environment variable naming a server") {
            scenario("wins, and nothing is built") {
                // PATH is set on every machine that can run this, so the assertion is about the
                // order and not about the fixture. `create` throws to prove it is never called.
                val service =
                    ContainerService.declare<GenericContainer<*>>(
                        name = "fake",
                        reusing = "PATH",
                        create = { error("a container was built despite the override") },
                        endpointOf = { error("unreachable") },
                    )

                service.endpoint shouldBe System.getenv("PATH")
                service.available shouldBe true
                service.describe() shouldContain "reusing the server named by PATH"
            }
        }

        feature("a service that cannot be had") {
            scenario("is unavailable rather than a thrown startup") {
                val service =
                    ContainerService.declare<GenericContainer<*>>(
                        name = "fake",
                        reusing = "NXGT_ABSENT_TEST_VARIABLE",
                        create = { error("this backend cannot start here") },
                        endpointOf = { error("unreachable") },
                    )

                service.available shouldBe false
                service.endpoint shouldBe null
            }
        }

        feature("a container, when there is no server to reuse").config(
            enabled = ContainerService.dockerReachable(),
        ) {
            // nginx over alpine: small, already on this machine, and it listens — so the endpoint
            // below is a real mapped port and not a number nobody checked.
            val service =
                ContainerService.declare(
                    name = "nginx",
                    reusing = "NXGT_ABSENT_TEST_VARIABLE",
                    create = {
                        GenericContainer("nginx:alpine")
                            .withExposedPorts(80)
                            .waitingFor(Wait.forListeningPort())
                    },
                    endpointOf = { "http://${it.host}:${it.getMappedPort(80)}" },
                )

            scenario("starts, and answers with somewhere reachable") {
                service.available shouldBe true
                service.endpoint shouldNotBe null
                service.describe() shouldContain "a container started for this run"
            }

            scenario("is started once, however many specs ask") {
                val first = service.endpoint
                service.endpoint shouldBe first
            }

            scenario("stops when told to, and still says truthfully where the run happened") {
                service.stop()
                service.stop() // stopping twice is what a shutdown hook after an explicit stop does

                service.describe() shouldContain "a container started for this run"
            }
        }

        feature("the teardown the run ends with") {
            // This was dead for a while and nothing noticed: the hook was built with
            // `Thread.startVirtualThread`, which starts it on the spot, so the JVM was handed a
            // terminated thread and never ran a teardown. Containers still vanished — Ryuk was doing
            // it — so the only way to see the bug is to assert on the hook itself.
            scenario("is registered unstarted, so the JVM has something left to start") {
                Registry.hook.state shouldBe Thread.State.NEW
            }

            scenario("is a virtual thread, since addShutdownHook leaves no choice but a thread") {
                Registry.hook.isVirtual shouldBe true
            }

            scenario("actually stops what the run started").config(
                enabled = ContainerService.dockerReachable(),
            ) {
                var built: GenericContainer<*>? = null
                val disposable =
                    ContainerService.declare(
                        name = "nginx-teardown",
                        reusing = "NXGT_ABSENT_TEST_VARIABLE",
                        create = {
                            GenericContainer("nginx:alpine")
                                .withExposedPorts(80)
                                .waitingFor(Wait.forListeningPort())
                                .also { built = it }
                        },
                        endpointOf = { "http://${it.host}:${it.getMappedPort(80)}" },
                    )

                disposable.available shouldBe true
                built?.isRunning shouldBe true

                Registry.teardown()

                built?.isRunning shouldBe false
            }
        }
    })
