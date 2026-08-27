package com.strange.jpa

import com.strange.jpa.entity.Thing
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The factory itself: what it needs to be built, and what closing it means. */
class JpaTest :
    FeatureSpec({

        feature("building a factory") {
            scenario("without an entity is refused, rather than producing one that maps nothing") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        Jpa.connect(JpaConfig(uri = "postgresql://localhost:5432/nothing"))
                    }

                failure.message shouldContain "at least one entity"
            }
        }

        feature("a factory that is open").config(enabled = JpaTestDatabase.available) {
            scenario("says so, and says otherwise once it is closed") {
                JpaTestDatabase.withSchema { schema ->
                    val jpa = JpaTestDatabase.connect(schema, Thing::class)

                    jpa.isOpen shouldBe true

                    jpa.close()

                    jpa.isOpen shouldBe false
                }
            }

            scenario("closing it twice is not an error") {
                JpaTestDatabase.withSchema { schema ->
                    val jpa = JpaTestDatabase.connect(schema, Thing::class)

                    // Ktor's DI closes every AutoCloseable it hands out, and so does whoever built
                    // this one. Hibernate's own close() is not the part that has to survive that —
                    // CloseGuard is.
                    jpa.close()
                    jpa.close()

                    jpa.isOpen shouldBe false
                }
            }

            scenario("and using it afterwards fails rather than reconnecting quietly") {
                JpaTestDatabase.withSchema { schema ->
                    val jpa = JpaTestDatabase.connect(schema, Thing::class)
                    jpa.close()

                    shouldThrowAny { jpa.transaction { it.find<Thing>(1L) } }
                }
            }
        }
    })
