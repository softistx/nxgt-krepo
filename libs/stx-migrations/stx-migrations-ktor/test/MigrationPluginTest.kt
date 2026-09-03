package com.softistx.migrations.ktor

import com.softistx.migrations.Migration
import com.softistx.migrations.MigrationContext
import com.softistx.migrations.MigrationFailedException
import com.softistx.migrations.MigrationHaltedException
import com.softistx.migrations.MigrationRecord
import com.softistx.migrations.MigrationRunner
import com.softistx.migrations.MigrationStatus
import com.softistx.migrations.ledger.InMemoryLedger
import com.softistx.migrations.ledger.MigrationLedger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.resolve
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/** A migration over the trivial context — the plugin's job has nothing to do with the store. */
private fun migration(
    at: Long,
    body: suspend () -> Unit = {},
) = object : Migration<Unit> {
    override val version = at
    override val description = "V$at"

    override suspend fun migrate(context: Unit) = body()
}

private fun runner(
    ledger: MigrationLedger,
    vararg migrations: Migration<Unit>,
) = MigrationRunner(ledger, migrations.toList(), MigrationContext { it(Unit) })

/**
 * The plugin doing the one thing an application cannot get right by accident: making the migrations a
 * condition of starting, rather than something that happens while requests are already arriving.
 *
 * No container here, and none needed. [InMemoryLedger] is the reference implementation — the claim is
 * conditional and the lock declines — so what these specs exercise is the gate. Which store is
 * underneath it is `stx-migrations-db`'s question, asked there against three of them.
 */
class MigrationPluginTest :
    FeatureSpec({

        feature("a migration that fails") {
            scenario("never reaches a route, because the application never finishes starting") {
                val served = mutableListOf<String>()

                shouldThrow<MigrationFailedException> {
                    testApplication {
                        application {
                            install(Migrations) {
                                gate(runner(InMemoryLedger(), migration(1) { error("no such column") }))
                            }
                            routing {
                                get("/") {
                                    served += "asked"
                                    call.respondText("up")
                                }
                            }
                        }
                        startApplication()
                    }
                }

                served shouldContainExactly emptyList()
            }

            scenario("halts the next startup too, while the failure stands") {
                val ledger = InMemoryLedger()
                shouldThrow<MigrationFailedException> {
                    testApplication {
                        application { install(Migrations) { gate(runner(ledger, migration(1) { error("no") })) } }
                        startApplication()
                    }
                }

                shouldThrow<MigrationHaltedException> {
                    testApplication {
                        application { install(Migrations) { gate(runner(ledger, migration(1), migration(2))) } }
                        startApplication()
                    }
                }
            }
        }

        feature("a migration that passes") {
            scenario("leaves the ledger where a route can read it") {
                val applied = mutableListOf<String>()
                testApplication {
                    application {
                        install(Migrations) {
                            gate(runner(InMemoryLedger(), migration(1) { applied += "V1" }, migration(2) { applied += "V2" }))
                        }
                        routing {
                            get("/") {
                                call.respondText(call.migrations.joinToString(",") { "${it.version}=${it.status}" })
                            }
                        }
                    }

                    client.get("/").bodyAsText() shouldBe "1=APPLIED,2=APPLIED"
                }

                applied shouldContainExactly listOf("V1", "V2")
            }

            scenario("runs before the first request, not beside it") {
                // The failure this plugin exists to remove: the runner it replaces was a suspending
                // ApplicationReadyEvent listener, which Spring does not wait for, so the port opened
                // while migrations were still running.
                val order = mutableListOf<String>()
                testApplication {
                    application {
                        install(Migrations) { gate(runner(InMemoryLedger(), migration(1) { order += "migrated" })) }
                        routing {
                            get("/") {
                                order += "served"
                                call.respondText("up")
                            }
                        }
                    }

                    client.get("/")
                }

                order shouldContainExactly listOf("migrated", "served")
            }

            scenario("more than one runner passes in the order they were added") {
                val order = mutableListOf<String>()
                testApplication {
                    application {
                        install(Migrations) {
                            gate(runner(InMemoryLedger(), migration(1) { order += "first ledger" }))
                            gate(runner(InMemoryLedger(), migration(1) { order += "second ledger" }))
                        }
                    }
                    startApplication()
                }

                order shouldContainExactly listOf("first ledger", "second ledger")
            }
        }

        feature("the ledger through Ktor's DI") {
            scenario("is the same list the plugin read, not a second reading of it") {
                testApplication {
                    application {
                        install(Migrations) {
                            gate(runner(InMemoryLedger(), migration(1)))
                        }
                        routing {
                            get("/") {
                                val injected = dependencies.resolve<List<MigrationRecord>>()
                                call.respondText("${injected.single().version}=${injected.single().status}")
                            }
                        }
                    }

                    client.get("/").bodyAsText() shouldBe "1=${MigrationStatus.APPLIED}"
                }
            }
        }
    })
