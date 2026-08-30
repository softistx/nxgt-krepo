package com.strange.koin

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.strange.amqp.Amqp
import com.strange.amqp.AmqpConfig
import com.strange.i18n.Messages
import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.query.nativeQuery
import com.strange.jpa.session.session
import com.strange.kafka.Kafka
import com.strange.kafka.KafkaConfig
import com.strange.koin.amqp.amqpModule
import com.strange.koin.entity.Entry
import com.strange.koin.i18n.messagesModule
import com.strange.koin.jpa.jpaModule
import com.strange.koin.jpa.jpaScanModule
import com.strange.koin.kafka.kafkaModule
import com.strange.koin.mongo.mongoModule
import com.strange.koin.redis.redisModule
import com.strange.koin.storage.storageModule
import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import com.strange.storage.ObjectStorage
import com.strange.storage.StorageConfig
import com.strange.testing.containers.minioContainer
import com.strange.testing.containers.mongoContainer
import com.strange.testing.containers.postgresContainer
import com.strange.testing.containers.rabbitContainer
import com.strange.testing.containers.redisContainer
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.firstOrNull
import org.koin.dsl.koinApplication
import java.util.Locale

/**
 * The modules against real backends, because what they promise — one instance, closed when the
 * container stops — is not observable from a mock.
 *
 * Each spec builds its own [koinApplication] rather than `startKoin`, so no two of them share a
 * global container and none of them leaves one behind for the next.
 */
class ModulesTest :
    FeatureSpec({

        val redis = redisContainer()
        val broker = rabbitContainer()
        val mongo = mongoContainer()
        val minio = minioContainer()
        val postgres = postgresContainer()

        feature("the Redis module").config(enabled = redis.available) {
            scenario("hands out one connection and closes it when the container stops") {
                val app = koinApplication { modules(redisModule(RedisConfig(uri = redis.requireEndpoint(), namespace = "koin"))) }
                val connection = app.koin.get<Redis>()

                connection.ping() shouldBe "PONG"
                connection shouldBeSameInstanceAs app.koin.get<Redis>()

                app.close()

                shouldThrowAny { connection.ping() }
            }
        }

        feature("the AMQP module").config(enabled = broker.available) {
            scenario("connects, though Koin cannot suspend and Amqp.connect does") {
                val app = koinApplication { modules(amqpModule(AmqpConfig(uri = broker.requireEndpoint(), connectionName = "koin"))) }
                val connection = app.koin.get<Amqp>()

                connection.isOpen shouldBe true

                app.close()

                connection.isOpen shouldBe false
            }
        }

        feature("the JPA module").config(enabled = postgres.available) {
            scenario("builds one factory, and it is a working one") {
                val app =
                    koinApplication {
                        modules(
                            jpaModule(
                                JpaConfig(
                                    uri = postgres.requireEndpoint().uri,
                                    username = postgres.requireEndpoint().username,
                                    password = postgres.requireEndpoint().password,
                                ),
                                Entry::class,
                            ),
                        )
                    }
                val jpa = app.koin.get<Jpa>()

                jpa shouldBeSameInstanceAs app.koin.get<Jpa>()

                // Building a factory connects to nothing, so without this the scenario would pass
                // against a server that refused every credential it was given.
                jpa.session { it.nativeQuery<Int>("select 1").single() } shouldBe 1

                app.close()

                jpa.isOpen shouldBe false
            }
        }

        feature("the JPA module given a package").config(enabled = postgres.available) {
            scenario("finds the entity on the classpath rather than being told it") {
                val app =
                    koinApplication {
                        modules(
                            jpaScanModule(
                                JpaConfig(
                                    uri = postgres.requireEndpoint().uri,
                                    username = postgres.requireEndpoint().username,
                                    password = postgres.requireEndpoint().password,
                                ),
                                "com.strange.koin.entity",
                            ),
                        )
                    }
                val jpa = app.koin.get<Jpa>()

                // Entry was never named here. The metamodel is what the scan produced, and asking it
                // rather than querying keeps this scenario off the server's schema, as the module's
                // other JPA scenario is careful to stay.
                jpa.factory.metamodel.entities
                    .map { it.name } shouldContain "Entry"

                app.close()
            }
        }

        feature("the Mongo module").config(enabled = mongo.available) {
            scenario("registers the client and the database over it, and closes the client") {
                val app = koinApplication { modules(mongoModule(mongo.requireEndpoint(), database = "koin-spec")) }
                val client = app.koin.get<MongoClient>()

                app.koin.get<MongoDatabase>().name shouldBe "koin-spec"
                client.listDatabaseNames().firstOrNull() shouldBe "admin"

                app.close()

                shouldThrowAny { client.listDatabaseNames().firstOrNull() }
            }
        }

        feature("the object-storage module").config(enabled = minio.available) {
            scenario("hands out a client that works, and closes it") {
                val endpoint = minio.requireEndpoint()
                val app =
                    koinApplication {
                        modules(storageModule(StorageConfig(endpoint.url, endpoint.accessKey, endpoint.secretKey)))
                    }
                val storage = app.koin.get<ObjectStorage>()

                storage.buckets()

                app.close()
            }
        }

        feature("the modules that open nothing") {
            scenario("the cluster is a description, and needs no broker to be injected") {
                val app = koinApplication { modules(kafkaModule(KafkaConfig(bootstrap = "example:9092"))) }

                app.koin.get<Kafka>().bootstrap shouldBe "example:9092"

                app.close()
            }

            scenario("the catalogs are the ones the caller loaded") {
                val messages = Messages.load(locales = listOf(Locale.ENGLISH))
                val app = koinApplication { modules(messagesModule(messages)) }

                app.koin.get<Messages>() shouldBeSameInstanceAs messages

                app.close()
            }
        }
    })
