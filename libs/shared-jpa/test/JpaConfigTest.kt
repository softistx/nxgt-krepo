package com.strange.jpa

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/** What the Kotlin fields become, since a mistyped Hibernate key is ignored rather than refused. */
class JpaConfigTest :
    FeatureSpec({

        feature("the settings a config produces") {
            scenario("names the ones a deployment sets, in milliseconds where Hibernate wants them") {
                val settings =
                    JpaConfig(
                        uri = "postgresql://db:5432/orders",
                        username = "orders",
                        password = "secret",
                        schema = "orders",
                        schemaMode = SchemaMode.VALIDATE,
                        connectTimeout = 2.seconds,
                        idleTimeout = 30.seconds,
                        statementCacheSize = 256,
                        batchSize = 50,
                    ).settings()

                settings shouldContain ("hibernate.connection.url" to "postgresql://db:5432/orders")
                settings shouldContain ("hibernate.default_schema" to "orders")
                settings shouldContain ("hibernate.hbm2ddl.auto" to "validate")
                settings shouldContain ("hibernate.vertx.pool.connect_timeout" to "2000")
                settings shouldContain ("hibernate.vertx.pool.idle_timeout" to "30000")
                settings shouldContain ("hibernate.vertx.prepared_statement_cache.max_size" to "256")
                settings shouldContain ("hibernate.jdbc.batch_size" to "50")
            }

            scenario("leaves out what was not set, rather than writing a default over the driver's") {
                val settings = JpaConfig().settings()

                settings shouldNotContainKey "hibernate.vertx.pool.connect_timeout"
                settings shouldNotContainKey "hibernate.jdbc.batch_size"
                settings shouldNotContainKey "hibernate.connection.username"
                settings shouldNotContainKey "hibernate.show_sql"
            }

            scenario("applies `properties` last, so it can override any of them") {
                val settings = JpaConfig(poolSize = 10, properties = mapOf("hibernate.connection.pool_size" to "40")).settings()

                settings["hibernate.connection.pool_size"] shouldBe "40"
            }
        }
    })
