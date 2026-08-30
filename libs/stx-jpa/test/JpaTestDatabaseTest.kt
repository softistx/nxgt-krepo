package com.strange.jpa

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * The harness the rest of this module's specs stand on, so that a failure here is read as "the
 * database is not what we thought" rather than as a bug in whatever spec noticed first.
 */
class JpaTestDatabaseTest :
    FeatureSpec({

        feature("the database the specs get").config(enabled = JpaTestDatabase.available) {
            scenario("is reachable, and its URI is the reactive spelling rather than a JDBC one") {
                JpaTestDatabase.endpoint.uri shouldStartWith "postgresql://"
                JpaTestDatabase.endpoint.username.isNotBlank() shouldBe true
            }

            scenario("hands each spec a schema of its own, and takes it away afterwards") {
                lateinit var used: String
                JpaTestDatabase.withSchema { schema ->
                    used = schema
                    schema shouldStartWith "stx_jpa_test_"
                }

                JpaTestDatabase.withSchema { next -> (next == used) shouldBe false }
            }
        }
    })
