package com.strange.jpa

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration
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

            scenario("says nothing about the JSON mapper, which is an object and not a string") {
                // Pinned because the obvious place to put it is here, and it cannot go here: the
                // mapper carries `JpaConfig.json`, so it reaches the factory as an instance through
                // the registry builder. A future edit that moved it into this map would be a class
                // name at best and silently ignored at worst.
                JpaConfig().settings() shouldNotContainKey "hibernate.type.json_format_mapper"
            }
        }

        feature("a configuration that could not work") {
            scenario("is refused where it was written, not where it hangs") {
                // A pool of none never hands out a connection, and `connectTimeout` defaults to
                // waiting a long time — so without this the service starts cleanly and every request
                // waits forever, with no exception anywhere to find afterwards.
                shouldThrow<IllegalArgumentException> { JpaConfig(poolSize = 0) }
                    .message
                    .shouldNotBeNull() shouldContain "never hands out a connection"
            }

            scenario("and so are the other sizes and timeouts that cannot mean anything") {
                shouldThrow<IllegalArgumentException> { JpaConfig(batchSize = 0) }
                shouldThrow<IllegalArgumentException> { JpaConfig(statementCacheSize = -1) }
                shouldThrow<IllegalArgumentException> { JpaConfig(connectTimeout = Duration.ZERO) }
                shouldThrow<IllegalArgumentException> { JpaConfig(idleTimeout = -1.seconds) }
            }

            scenario("while the ordinary ones are accepted unchanged") {
                JpaConfig(poolSize = 1, batchSize = 1, statementCacheSize = 0).poolSize shouldBe 1
            }
        }
    })
