package com.softistx.jpa

import com.softistx.jpa.criteria.fetchEach
import com.softistx.jpa.entity.Labelled
import com.softistx.jpa.entity.TicketState
import com.softistx.jpa.query.query
import com.softistx.jpa.session.createQuery
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * An enum and a collection of values — the two shapes a column takes when it is not a scalar.
 *
 * `@ElementCollection` is the one that matters, and not for the reason it looks: it is a plural
 * attribute, so the module's central rule applies to it exactly as it applies to `@OneToMany`. It is
 * lazy, and reading it unfetched throws. Nothing said so, and it is not obvious from the annotation,
 * which is why it is measured here rather than assumed.
 */
class ValueMappingTest :
    FeatureSpec({

        feature("@Enumerated(STRING)").config(enabled = JpaTestDatabase.available) {
            scenario("stores the name, so the column survives a reordered enum") {
                JpaTestDatabase.withJpa(Labelled::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Labelled(1).also { it.state = TicketState.CLOSED })
                    }

                    jpa.session { session -> session.get<Labelled>(1L).state } shouldBe TicketState.CLOSED
                    JpaTestDatabase.columnType(jpa.config.schema!!, "labelled", "state") shouldBe "character varying"
                }
            }
        }

        feature("@ElementCollection").config(enabled = JpaTestDatabase.available) {
            scenario("is a table of its own, named after the owner and the property") {
                JpaTestDatabase.withJpa(Labelled::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Labelled(1).also { it.tags = mutableSetOf("red", "blue") })
                    }

                    JpaTestDatabase.rows(jpa.config.schema!!, "labelled_tags") shouldBe 2
                }
            }

            scenario("is lazy like any other plural attribute, so unfetched it throws") {
                // The rule the module is built on reaches here too. A collection of strings looks
                // like a column and is not one.
                JpaTestDatabase.withJpa(Labelled::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Labelled(1).also { it.tags = mutableSetOf("red", "blue") })
                    }

                    shouldThrowAny {
                        jpa.session { session -> session.query<Labelled>("from Labelled").list().map { it.tags.size } }
                    }
                }
            }

            scenario("and fetchEach loads it in the same statement") {
                JpaTestDatabase.withJpa(Labelled::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Labelled(1).also { it.tags = mutableSetOf("red", "blue") })
                    }

                    jpa.session { session ->
                        val criteria = session.createQuery<Labelled>()
                        criteria.from(Labelled::class.java).fetchEach(Labelled::tags)

                        session
                            .query(criteria)
                            .list()
                            .single()
                            .tags
                    } shouldContainExactlyInAnyOrder listOf("red", "blue")
                }
            }
        }
    })
