package com.strange.jpa.dsl

import com.strange.jpa.Jpa
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.JpaUnrestrictedMutationException
import com.strange.jpa.entity.Thing
import com.strange.jpa.query.mutate
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.statelessTransaction
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The write side of the DSL: what it changes, what it refuses, and that it agrees with HQL. */
class MutateDslTest :
    FeatureSpec({

        suspend fun JpaSession.seed(vararg names: String) {
            names.forEachIndexed { index, name -> persist(Thing((index + 1).toLong(), name)) }
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Thing::class) { jpa ->
                jpa.transaction { it.seed("one", "two", "three", "four") }
                block(jpa)
            }

        suspend fun Jpa.names(): List<String> = session { it.select<Thing> { orderBy { asc(Thing::id) } }.list() }.map { it.name }

        feature("an update").config(enabled = JpaTestDatabase.available) {
            scenario("assigns what it was told, to the rows it was told") {
                seeded { jpa ->
                    val touched =
                        jpa.transaction { session ->
                            session
                                .update<Thing> {
                                    set(Thing::name, "changed")
                                    where { Thing::id le 2L }
                                }.execute()
                        }

                    touched shouldBe 2
                    jpa.names() shouldContainExactly listOf("changed", "changed", "three", "four")
                }
            }

            scenario("touches the same rows, and reports the same count, as the HQL for it") {
                seeded { jpa ->
                    val viaHql =
                        jpa.transaction { session ->
                            session.mutate("update Thing set name = 'x' where id > :id").parameter("id", 2L).execute()
                        }
                    val viaDsl =
                        jpa.transaction { session ->
                            session
                                .update<Thing> {
                                    set(Thing::name, "x")
                                    where { Thing::id gt 2L }
                                }.execute()
                        }

                    viaDsl shouldBe viaHql
                    jpa.names() shouldContainExactly listOf("one", "two", "x", "x")
                }
            }

            scenario("assigns a column from itself, in one statement") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { it.persist(Thing(1, "a"), Thing(2, "b")) }

                    jpa.transaction { session ->
                        session
                            .update<Thing> {
                                set(Thing::id, this[Thing::id] + 10L)
                                where { Thing::id le 2L }
                            }.execute()
                    }

                    jpa
                        .session { it.select<Thing> { orderBy { asc(Thing::id) } }.list() }
                        .map { it.id } shouldContainExactly listOf(11L, 12L)
                }
            }

            scenario("refuses to touch every row unless that is said out loud") {
                seeded { jpa ->
                    val refused =
                        shouldThrow<JpaUnrestrictedMutationException> {
                            jpa.transaction { session ->
                                session.update<Thing> { set(Thing::name, "all") }.execute()
                            }
                        }

                    refused.message shouldContain "everyRow()"
                    jpa.names() shouldContainExactly listOf("one", "two", "three", "four")

                    jpa.transaction { session ->
                        session
                            .update<Thing> {
                                set(Thing::name, "all")
                                everyRow()
                            }.execute()
                    } shouldBe 4
                }
            }

            scenario("takes its restrictions from the chain as readily as from the block") {
                seeded { jpa ->
                    val touched =
                        jpa.transaction { session ->
                            session
                                .update<Thing> { set(Thing::name, "chained") }
                                .where { Thing::id ge 2L }
                                .where { Thing::id le 3L }
                                .execute()
                        }

                    touched shouldBe 2
                    jpa.names() shouldContainExactly listOf("one", "chained", "chained", "four")
                }
            }

            scenario("refuses a where block that turned out to add nothing") {
                seeded { jpa ->
                    val filter: Long? = null

                    shouldThrow<JpaUnrestrictedMutationException> {
                        jpa.transaction { session ->
                            session
                                .update<Thing> {
                                    set(Thing::name, "all")
                                    where { filter?.let { id -> Thing::id eq id } }
                                }.execute()
                        }
                    }

                    jpa.names() shouldContainExactly listOf("one", "two", "three", "four")
                }
            }
        }

        feature("a delete").config(enabled = JpaTestDatabase.available) {
            scenario("removes the rows it was told, and counts them") {
                seeded { jpa ->
                    val gone =
                        jpa.transaction { session ->
                            session.delete<Thing>().where { Thing::name oneOf listOf("two", "four") }.execute()
                        }

                    gone shouldBe 2
                    jpa.names() shouldContainExactly listOf("one", "three")
                }
            }

            scenario("refuses to empty the table unless that is said out loud") {
                seeded { jpa ->
                    shouldThrow<JpaUnrestrictedMutationException> {
                        jpa.transaction { session -> session.delete<Thing>().execute() }
                    }

                    jpa.names().size shouldBe 4

                    jpa.transaction { session -> session.delete<Thing>().everyRow().execute() } shouldBe 4
                    jpa.names().shouldContainExactly(emptyList())
                }
            }
        }

        feature("on a stateless session").config(enabled = JpaTestDatabase.available) {
            scenario("the DSL and the entity operations keep their own names") {
                seeded { jpa ->
                    // `update(entity)` and `update { }` are both members here, and both resolve:
                    // the entity one is the stateless vocabulary, the block one is this DSL.
                    val touched =
                        jpa.statelessTransaction { session ->
                            session.update(Thing(1, "renamed"))
                            session
                                .update<Thing> {
                                    set(Thing::name, "bulk")
                                    where { Thing::id ge 3L }
                                }.execute()
                        }

                    touched shouldBe 2
                    jpa.names() shouldContainExactly listOf("renamed", "two", "bulk", "bulk")
                }
            }
        }
    })
