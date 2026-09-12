package com.softistx.jpa

import com.softistx.jpa.entity.Address
import com.softistx.jpa.entity.ConvertedJson
import com.softistx.jpa.entity.ConvertedPlain
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * An `AttributeConverter` doing the serialization, instead of the `FormatMapper` doing it.
 *
 * It is the obvious alternative to this module's kotlinx mapper, and the two are not interchangeable
 * — which is worth measuring rather than asserting, because the difference is a column type and a
 * dirty check, neither of which the annotation shows.
 */
class ConvertedJsonTest :
    FeatureSpec({

        feature("a converter alone").config(enabled = JpaTestDatabase.available) {
            scenario("round-trips the document, into whatever column a String gets") {
                JpaTestDatabase.withJpa(ConvertedPlain::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(ConvertedPlain(1, Address("1 Main", "Berlin")))
                    }

                    jpa.session { session -> session.get<ConvertedPlain>(1L).address } shouldBe
                        Address("1 Main", "Berlin")

                    // The cost of the route: a character column, so none of Postgres's JSON
                    // operators, no GIN index, and no HQL path into the document.
                    JpaTestDatabase.columnType(jpa.config.schema!!, "converted_plain", "address") shouldBe
                        "character varying"
                }
            }
        }

        feature("a converter asked for a JSON column too").config(enabled = JpaTestDatabase.available) {
            scenario("keeps jsonb, and the converter is what wrote it") {
                JpaTestDatabase.withJpa(ConvertedJson::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(ConvertedJson(1, Address("1 Main", "Berlin")))
                    }

                    JpaTestDatabase.columnType(jpa.config.schema!!, "converted_json", "address") shouldBe "jsonb"
                    jpa.session { session -> session.get<ConvertedJson>(1L).address } shouldBe
                        Address("1 Main", "Berlin")
                }
            }
        }
    })
