package com.strange.jpa.json

import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Coordinates
import com.strange.jpa.entity.Place
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * The other route into a JSON column, and the one to reach for first.
 *
 * An `@Embeddable` is written by Hibernate from its own mapping model — no `@Serializable`, no format
 * mapper, nothing from this module involved. What that buys is in the last scenario: Hibernate knows
 * the shape, so HQL can path into the document and the query is checked against the mapping at
 * startup rather than against the database at the moment it runs.
 */
class EmbeddedJsonTest :
    FeatureSpec({

        feature("an embeddable mapped as JSON").config(enabled = JpaTestDatabase.available) {
            scenario("is jsonb, like the opaque form") {
                JpaTestDatabase.withJpa(Place::class) { jpa ->
                    JpaTestDatabase.columnType(jpa.config.schema!!, "places", "at") shouldBe "jsonb"
                }
            }

            scenario("round trips without a serializer anywhere in sight") {
                JpaTestDatabase.withJpa(Place::class) { jpa ->
                    jpa.transaction { it.persist(Place(1, Coordinates(52.52, 13.40))) }

                    jpa.session { it.get<Place>(1L) }.at.latitude shouldBe 52.52
                }
            }

            scenario("and can be queried by HQL through its properties") {
                JpaTestDatabase.withJpa(Place::class) { jpa ->
                    jpa.transaction {
                        it.persist(Place(1, Coordinates(52.52, 13.40)))
                        it.persist(Place(2, Coordinates(48.86, 2.35)))
                    }

                    // The whole argument for this route in one line: `at.latitude` is a mapped path
                    // into a jsonb document. The opaque form cannot do this — its contents are a
                    // string as far as the mapping is concerned.
                    jpa.session {
                        it
                            .query<Long>("select p.id from Place p where p.at.latitude > :south")
                            .parameter("south", 50.0)
                            .single()
                    } shouldBe 1L
                }
            }
        }
    })
