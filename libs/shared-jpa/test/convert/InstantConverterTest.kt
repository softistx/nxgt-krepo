package com.strange.jpa.convert

import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.future.await
import kotlin.time.Instant

/** That a `kotlin.time.Instant` reaches Postgres as a timestamp, and comes back to the nanosecond. */
class InstantConverterTest :
    FeatureSpec({

        feature("a kotlin.time.Instant attribute").config(enabled = JpaTestDatabase.available) {
            scenario("is stored as a timestamp with time zone, not as a blob") {
                JpaTestDatabase.withJpa(Stamped::class) { jpa ->
                    val schema = requireNotNull(jpa.config.schema)

                    JpaTestDatabase.columnType(schema, "stamped", "at") shouldBe "timestamp with time zone"
                }
            }

            scenario("comes back as the instant that went in") {
                JpaTestDatabase.withJpa(Stamped::class) { jpa ->
                    val at = Instant.parse("2026-08-27T10:15:30.123456Z")

                    jpa.transaction { it.persist(Stamped(1, at = at)).await() }

                    jpa.session { it.find(Stamped::class.java, 1L).await() }.at shouldBe at
                }
            }
        }
    })
