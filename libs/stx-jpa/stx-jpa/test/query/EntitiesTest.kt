package com.softistx.jpa.query

import com.softistx.jpa.JpaNotFoundException
import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.entity.Thing
import com.softistx.jpa.session.session
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** The one-shot operations, each of which is a transaction of its own. */
class EntitiesTest :
    FeatureSpec({

        feature("an operation on Jpa").config(enabled = JpaTestDatabase.available) {
            scenario("persists and reads back without a session block") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.persist(Thing(1, "written"), Thing(2, "also written"))

                    jpa.find<Thing>(1)?.name shouldBe "written"
                    jpa.get<Thing>(2).name shouldBe "also written"
                }
            }

            scenario("answers null for a missing id, and throws when asked to get one") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.find<Thing>(404).shouldBeNull()

                    val failure = shouldThrow<JpaNotFoundException> { jpa.get<Thing>(404) }

                    failure.type shouldBe Thing::class
                    failure.id shouldBe 404
                }
            }

            scenario("merges a detached instance onto the stored one") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.persist(Thing(1, "before"))

                    jpa.merge(Thing(1, "after")).name shouldBe "after"

                    jpa.find<Thing>(1)?.name shouldBe "after"
                }
            }

            scenario("removes by id, and says whether there was anything there") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.persist(Thing(1, "doomed"))

                    jpa.removeById<Thing>(1) shouldBe true
                    jpa.removeById<Thing>(1) shouldBe false

                    jpa.find<Thing>(1).shouldBeNull()
                }
            }

            scenario("removes an instance that another session loaded and detached") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.persist(Thing(1, "doomed"))

                    // Detached the moment that session's transaction ended, which is the only kind
                    // of instance a one-shot remove can ever be handed.
                    val loaded = jpa.session { it.get<Thing>(1) }
                    jpa.remove(loaded)

                    jpa.find<Thing>(1).shouldBeNull()
                }
            }
        }
    })
