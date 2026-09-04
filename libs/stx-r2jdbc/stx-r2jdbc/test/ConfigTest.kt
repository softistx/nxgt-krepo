package com.softistx.r2jdbc

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.seconds

/**
 * What is refused before anything connects.
 *
 * No container. Every one of these is a value that would otherwise start a service cleanly and fail
 * it later, somewhere with no useful stack — `JpaConfig` states the rule and this follows it.
 */
class ConfigTest :
    FeatureSpec({

        feature("the pool size") {
            scenario("cannot be none, because a pool of none never hands out a connection") {
                shouldThrow<IllegalArgumentException> { R2jdbcConfig(poolSize = 0) }
                    .message shouldContain "never hands out a connection"
            }
        }

        feature("the timeouts") {
            scenario("cannot be zero or negative") {
                shouldThrow<IllegalArgumentException> { R2jdbcConfig(connectTimeout = 0.seconds) }
                shouldThrow<IllegalArgumentException> { R2jdbcConfig(idleTimeout = (-1).seconds) }
                shouldNotThrowAny { R2jdbcConfig(connectTimeout = 1.seconds, idleTimeout = 30.seconds) }
            }
        }

        feature("the schema") {
            scenario("must be a plain identifier, because it is written into `set search_path`") {
                // It cannot be a bind parameter — `set` takes an identifier — so the alternative to
                // checking it here is quoting it there and hoping.
                shouldThrow<IllegalArgumentException> { R2jdbcConfig(schema = "public\"; drop schema x --") }
                shouldThrow<IllegalArgumentException> { R2jdbcConfig(schema = "9lives") }
                shouldNotThrowAny { R2jdbcConfig(schema = "stx_r2jdbc_1") }
            }
        }

        feature("the uri") {
            scenario("names the backend, and the placeholder that comes with it") {
                Backend.of("postgresql://localhost:5432/x") shouldBe Backend.POSTGRES
                Backend.of("mysql://localhost:3306/x") shouldBe Backend.MYSQL
                Backend.of("mariadb://localhost:3306/x") shouldBe Backend.MYSQL
            }

            scenario("is refused as a JDBC URL, by name") {
                // The mistake this repo's URIs invite: every library here takes the reactive
                // spelling, and the two differ by five characters a copy from a properties file
                // carries along.
                shouldThrow<R2jdbcException> { Backend.of("jdbc:postgresql://localhost:5432/x") }
                    .message shouldContain "nothing in this repository speaks JDBC"
            }

            scenario("is refused for a server nothing has measured") {
                shouldThrow<R2jdbcException> { Backend.of("db2://localhost:50000/x") }
                    .message shouldContain "PostgreSQL and MySQL"
            }
        }
    })
