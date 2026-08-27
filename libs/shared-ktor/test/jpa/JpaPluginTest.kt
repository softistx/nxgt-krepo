package com.strange.ktor.jpa

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.SchemaMode
import com.strange.jpa.session.transaction
import com.strange.testing.containers.postgresContainer
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
import io.vertx.core.Vertx
import io.vertx.pgclient.PgBuilder
import io.vertx.pgclient.PgConnectOptions
import io.vertx.sqlclient.PoolOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await

/**
 * One session factory for the application, and a route that crosses a suspension point inside a
 * transaction.
 *
 * The `delay` in the first scenario is the point of the whole exercise: a Hibernate Reactive session
 * belongs to the Vert.x context that opened it, and a route that suspends mid-transaction is the
 * ordinary case rather than an exotic one. Without the confinement bridge in `shared-jpa` this
 * scenario is where it would show up, as an HR000069 on the second statement.
 */
class JpaPluginTest :
    FeatureSpec({

        val postgres = postgresContainer()
        val schema = "shared_ktor_jpa_test"

        /** The specs own a schema of their own on whatever server answered, and leave nothing behind. */
        suspend fun schema(sql: String) {
            val endpoint = postgres.endpoint!!
            val vertx = Vertx.vertx()
            val pool =
                PgBuilder
                    .pool()
                    .connectingTo(
                        PgConnectOptions
                            .fromUri(endpoint.uri)
                            .setUser(endpoint.username)
                            .setPassword(endpoint.password),
                    ).with(PoolOptions().setMaxSize(1))
                    .using(vertx)
                    .build()
            try {
                pool
                    .query(sql)
                    .execute()
                    .toCompletionStage()
                    .await()
            } finally {
                pool.close().toCompletionStage().await()
                vertx.close().toCompletionStage().await()
            }
        }

        fun config() =
            JpaConfig(
                uri = postgres.endpoint!!.uri,
                username = postgres.endpoint!!.username,
                password = postgres.endpoint!!.password,
                schema = schema,
                schemaMode = SchemaMode.CREATE_DROP,
            )

        beforeSpec { if (postgres.available) schema("create schema if not exists $schema") }
        afterSpec { if (postgres.available) schema("drop schema if exists $schema cascade") }

        feature("a route reaching for the factory").config(enabled = postgres.available) {
            scenario("writes and reads across a suspension point inside one transaction") {
                testApplication {
                    application {
                        install(JpaConnection) {
                            config = config()
                            entities(Note::class)
                        }
                        routing {
                            get("/") {
                                val text =
                                    call.jpa.transaction { session ->
                                        session.persist(Note(1, "written"))
                                        delay(1)
                                        session.get<Note>(1).text
                                    }
                                call.respondText(text)
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "written"
                }
            }

            scenario("and the factory is closed when the application stops") {
                lateinit var captured: Jpa
                testApplication {
                    application {
                        install(JpaConnection) {
                            config = config()
                            entities(Note::class)
                        }
                        routing {
                            get("/") {
                                captured = call.jpa
                                call.respondText("ok")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "ok"
                }

                captured.isOpen shouldBe false
            }
        }

        feature("a factory made injectable").config(enabled = postgres.available) {
            scenario("is the one the plugin built, not a second one") {
                lateinit var injected: Jpa
                testApplication {
                    application {
                        install(JpaConnection) {
                            config = config()
                            entities(Note::class)
                            injectable = true
                        }

                        injected = dependencies.resolve()

                        routing { get("/") { call.respondText("${call.jpa === injected}") } }
                    }

                    client.get("/").bodyAsText() shouldBe "true"
                }

                // Closed twice — once by the plugin that opened it, once by the container that was
                // handed it — and neither notices, because `Jpa.close` is guarded.
                injected.isOpen shouldBe false
                injected.close()
            }
        }

        feature("a factory handed in rather than built").config(enabled = postgres.available) {
            scenario("is the one routes get, and is still open after the application stops") {
                val mine = Jpa.connect(config(), listOf(Note::class))
                try {
                    lateinit var captured: Jpa
                    testApplication {
                        application {
                            install(JpaConnection) { instance = mine }
                            routing {
                                get("/") {
                                    captured = call.jpa
                                    call.respondText("ok")
                                }
                            }
                        }
                        client.get("/").bodyAsText() shouldBe "ok"
                    }

                    captured shouldBeSameInstanceAs mine
                    mine.isOpen shouldBe true
                } finally {
                    mine.close()
                }
            }
        }

        feature("reaching for it without installing it") {
            scenario("names the plugin") {
                testApplication {
                    application {
                        val failure = shouldThrow<IllegalStateException> { jpa }

                        failure.message shouldContain "JpaConnection"
                    }

                    startApplication()
                }
            }
        }
    })
