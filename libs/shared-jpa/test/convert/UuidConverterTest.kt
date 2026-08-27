package com.strange.jpa.convert

import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Stamped
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** That a `kotlin.uuid.Uuid` is Postgres' own `uuid` column, and not a string that looks like one. */
@OptIn(ExperimentalUuidApi::class)
class UuidConverterTest :
    FeatureSpec({

        feature("a kotlin.uuid.Uuid attribute").config(enabled = JpaTestDatabase.available) {
            scenario("is stored as a uuid, not as text") {
                JpaTestDatabase.withJpa(Stamped::class) { jpa ->
                    val schema = requireNotNull(jpa.config.schema)

                    JpaTestDatabase.columnType(schema, "stamped", "reference") shouldBe "uuid"
                }
            }

            scenario("comes back as the uuid that went in") {
                JpaTestDatabase.withJpa(Stamped::class) { jpa ->
                    val reference = Uuid.parse("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

                    jpa.transaction { it.persist(Stamped(1, reference = reference)) }

                    jpa.session { it.get<Stamped>(1L) }.reference shouldBe reference
                }
            }
        }
    })
