package com.strange.ktor.storage

import com.strange.storage.ObjectStorage
import com.strange.storage.StorageConfig
import com.strange.testing.containers.minioContainer
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/** One object-storage client for the application, closed on stop. */
class StoragePluginTest :
    FeatureSpec({

        val server = minioContainer()

        fun config(): StorageConfig = server.endpoint!!.let { StorageConfig(it.url, it.accessKey, it.secretKey) }

        feature("a route reaching for the store").config(enabled = server.available) {
            scenario("gets a client that can make and drop a bucket") {
                testApplication {
                    application {
                        install(Storage) { config = config() }
                        routing {
                            get("/") {
                                val name = "shared-ktor-spec-${System.nanoTime()}"
                                call.storage.ensureBucket(name)
                                val existed = call.storage.bucketExists(name)
                                call.storage.deleteBucket(name)

                                call.respondText("$existed:${call.storage.bucketExists(name)}")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "true:false"
                }
            }

            scenario("and it is closed when the application stops") {
                lateinit var captured: ObjectStorage
                testApplication {
                    application {
                        install(Storage) { config = config() }
                        routing {
                            get("/") {
                                captured = call.storage
                                call.respondText("ok")
                            }
                        }
                    }
                    client.get("/").bodyAsText() shouldBe "ok"
                }

                shouldThrowAny { captured.buckets() }
            }
        }

        feature("a client handed in rather than built").config(enabled = server.available) {
            scenario("is the one routes get, and is still open after the application stops") {
                val mine = ObjectStorage.connect(config())
                try {
                    lateinit var captured: ObjectStorage
                    testApplication {
                        application {
                            install(Storage) { instance = mine }
                            routing {
                                get("/") {
                                    captured = call.storage
                                    call.respondText("ok")
                                }
                            }
                        }
                        client.get("/").bodyAsText() shouldBe "ok"
                    }

                    captured shouldBeSameInstanceAs mine
                    mine.buckets()
                } finally {
                    mine.close()
                }
            }
        }

        feature("installing it wrongly") {
            scenario("no config is a failure to start, since two of its fields are credentials") {
                shouldThrowAny {
                    testApplication {
                        application { install(Storage) {} }
                        client.get("/")
                    }
                }
            }
        }
    })
