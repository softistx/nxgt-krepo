package com.strange.ktor.mongo

import com.strange.mongo.collection
import com.strange.testing.containers.mongoContainer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Not `private`: a file-private class is one the driver's codec cannot reach by reflection, and it
// says so with an IllegalAccessException that mentions neither the class nor the reason.
@Serializable
internal data class Note(
    @SerialName("_id") val id: String,
    val text: String,
    // The annotation is the usage `mongoSerializersModule` documents; without it the field has no
    // codec and the insert fails. Which is the happier of the two ways to get this wrong.
    @Contextual val at: Instant,
)

/**
 * The database handle a route gets, and the codec registry underneath it.
 *
 * The `Instant` in [Note] is the point of the second scenario: a client built without
 * `mongoCodecRegistry()` stores it as something this library cannot read back, and every step up to
 * that one succeeds. Round-tripping it is the only way to see the difference.
 */
class MongoDBTest :
    FeatureSpec({

        val server = mongoContainer()

        feature("a route reaching for the database").config(enabled = server.available) {
            scenario("gets the one the plugin was configured with") {
                testApplication {
                    application {
                        install(MongoDB) {
                            uri = server.endpoint!!
                            database = "shared-ktor-spec"
                        }
                        routing { get("/") { call.respondText(call.database.name) } }
                    }
                    client.get("/").bodyAsText() shouldBe "shared-ktor-spec"
                }
            }

            scenario("with the codec registry, so an Instant survives the round trip") {
                testApplication {
                    application {
                        install(MongoDB) {
                            uri = server.endpoint!!
                            database = "shared-ktor-spec"
                        }
                        routing {
                            get("/") {
                                // The failure is answered rather than thrown: Ktor's test engine
                                // turns an exception into a 500 page that says nothing, and a codec
                                // that is missing should name itself in the assertion diff.
                                val outcome =
                                    runCatching {
                                        val notes = call.database.collection<Note>("notes")
                                        val written =
                                            Note("n1", "one", Instant.fromEpochMilliseconds(1_700_000_000_000))
                                        notes.insertOne(written)

                                        val read = notes.find().firstOrNull()

                                        // Dropped here rather than after: the override path points
                                        // these specs at a server that is not theirs.
                                        call.database.drop()
                                        "${read?.at == written.at}"
                                    }

                                call.respondText(outcome.getOrElse { "${it::class.simpleName}: ${it.message}" })
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "true"
                }
            }

            scenario("and the client is closed when the application stops") {
                lateinit var captured: com.mongodb.kotlin.client.coroutine.MongoDatabase
                testApplication {
                    application {
                        install(MongoDB) {
                            uri = server.endpoint!!
                            database = "shared-ktor-spec"
                        }
                        routing {
                            get("/") {
                                captured = call.database
                                call.respondText("ok")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "ok"
                }

                shouldThrowAny { captured.listCollectionNames().firstOrNull() }
            }
        }

        feature("reaching for it without installing it") {
            scenario("names the plugin") {
                // Asserted on the accessor rather than through the client: Ktor's test engine turns
                // a handler exception into a 500 page, so going over HTTP would prove only that
                // something went wrong, not that the message says which install is missing.
                testApplication {
                    application {
                        val failure = shouldThrow<IllegalStateException> { database }

                        failure.message shouldContain "MongoDB"
                    }

                    startApplication()
                }
            }
        }
    })
