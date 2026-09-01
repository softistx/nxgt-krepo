package com.strange.workflow.ktor

import com.strange.workflow.Workflow
import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.StepScope
import com.strange.workflow.dsl.sleep
import com.strange.workflow.dsl.step
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.workflow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class Order(
    val paid: Boolean = false,
    val shipped: Boolean = false,
)

private fun order(): Workflow<Order> =
    workflow<Order>("order") {
        step("pay") { context.copy(paid = true) }
        step("ship") { context.copy(shipped = true) }
    }

private fun napping(): Workflow<Order> =
    workflow<Order>("napping") {
        step("pay") { context.copy(paid = true) }
        sleep("settle", 100.milliseconds)
        step("ship") { context.copy(shipped = true) }
    }

/**
 * The plugin doing the two things an application cannot get right by accident: one engine for the
 * process, and a worker that stops when the application does.
 */
class WorkflowPluginTest :
    FeatureSpec({

        feature("a route reaching for the engine") {
            scenario("gets one that runs a registered workflow") {
                val flow = order()
                testApplication {
                    application {
                        install(Workflows) {
                            store = InMemoryStore()
                            register(flow)
                        }
                        routing {
                            get("/") {
                                call.respondText(
                                    call.workflows
                                        .start(flow, Order())
                                        .status.name,
                                )
                            }
                        }
                    }

                    client.get("/").bodyAsText() shouldBe "Completed"
                }
            }

            scenario("it is one engine, not one per request") {
                val flow = order()
                testApplication {
                    application {
                        install(Workflows) {
                            store = InMemoryStore()
                            register(flow)
                        }
                        routing { get("/") { call.respondText("${System.identityHashCode(call.workflows)}") } }
                    }
                    val first = client.get("/").bodyAsText()

                    client.get("/").bodyAsText() shouldBe first
                }
            }

            scenario("an engine built elsewhere is adopted rather than replaced") {
                val flow = order()
                val mine = WorkflowEngine(InMemoryStore()) { register(flow) }
                testApplication {
                    application {
                        install(Workflows) { instance = mine }
                        routing { get("/") { call.respondText("${System.identityHashCode(call.workflows)}") } }
                    }

                    client.get("/").bodyAsText() shouldBe "${System.identityHashCode(mine)}"
                }
            }

            scenario("without the plugin, the failure names the plugin") {
                testApplication {
                    application {
                        // Asked of the application rather than through a route: Ktor turns an
                        // exception in a handler into a 500, which would hide the message that is
                        // the whole point of this — the name of the install that is missing.
                        shouldThrow<IllegalStateException> { workflows }.message shouldContain "Workflows"
                    }

                    startApplication()
                }
            }
        }

        feature("the worker") {
            scenario("off by default, so nothing wakes a sleeping instance") {
                val flow = napping()
                val store = InMemoryStore()
                testApplication {
                    application {
                        install(Workflows) {
                            this.store = store
                            register(flow)
                        }
                        routing { get("/") { call.respondText(call.workflows.start(flow, Order()).id) } }
                    }
                    val id = client.get("/").bodyAsText()

                    delay(300)
                    store.load(id)!!.status shouldBe WorkflowStatus.Sleeping
                }
            }

            scenario("switched on, it wakes it") {
                val flow = napping()
                val store = InMemoryStore()
                testApplication {
                    application {
                        install(Workflows) {
                            this.store = store
                            register(flow)
                            worker = true
                            poll = 25.milliseconds
                        }
                        routing { get("/") { call.respondText(call.workflows.start(flow, Order()).id) } }
                    }
                    val id = client.get("/").bodyAsText()

                    withTimeout(10.seconds) {
                        while (store.load(id)!!.status != WorkflowStatus.Completed) delay(25)
                    }
                }
            }
        }

        feature("dependency injection") {
            scenario("the engine the plugin built is the one the container hands out") {
                val flow = order()
                testApplication {
                    application {
                        install(Workflows) {
                            store = InMemoryStore()
                            register(flow)
                            injectable = true
                        }
                        routing {
                            get("/") {
                                val injected = dependencies.resolve<WorkflowEngine>()
                                injected shouldBeSameInstanceAs call.workflows
                                call.respondText("same")
                            }
                        }
                    }

                    client.get("/").bodyAsText() shouldBe "same"
                }
            }
        }
    })
