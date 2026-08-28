package com.strange.jpa.dsl

import com.strange.jpa.Jpa
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** A restriction named once and used by more than one query. */
class SpecsTest :
    FeatureSpec({

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            val bo = Buyer(2, "bo", null)
            persist(ada, bo)
            persist(
                Purchase(1, "P-1", 150, ada),
                Purchase(2, "P-2", 50, bo),
                Purchase(3, "P-3", 400, bo),
            )
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { it.seed() }
                block(jpa)
            }

        val large: JpaSpec<Purchase> = { Purchase::total gt 100L }
        val adas: JpaSpec<Purchase> = { join(Purchase::customer)[Buyer::name] eq "ada" }
        val nothing: JpaSpec<Purchase> = { null }

        feature("a specification").config(enabled = JpaTestDatabase.available) {
            scenario("is the type where already takes, so it needs no overload to be passed") {
                seeded { jpa ->
                    val (rows, counted) =
                        jpa.session { session ->
                            session
                                .select<Purchase>()
                                .where(large)
                                .orderBy { asc(Purchase::reference) }
                                .list() to
                                session.select<Purchase>().where(large).count()
                        }

                    rows.map { it.reference } shouldContainExactly listOf("P-1", "P-3")
                    counted shouldBe 2L
                }
            }

            scenario("fits a projection as well as a selection, being written against the join scope") {
                seeded { jpa ->
                    val references =
                        jpa.session { session ->
                            session
                                .project<Purchase, String> { this[Purchase::reference] }
                                .where(large)
                                .orderBy { asc(Purchase::reference) }
                                .list()
                        }

                    references shouldContainExactly listOf("P-1", "P-3")
                }
            }

            scenario("says or, which a chain of wheres cannot") {
                seeded { jpa ->
                    val (either, both) =
                        jpa.session { session ->
                            session
                                .select<Purchase>()
                                .where(large or adas)
                                .orderBy { asc(Purchase::reference) }
                                .list() to
                                session
                                    .select<Purchase>()
                                    .where(large)
                                    .where(adas)
                                    .list()
                        }

                    either.map { it.reference } shouldContainExactly listOf("P-1", "P-3")
                    both.map { it.reference } shouldContainExactly listOf("P-1")
                }
            }

            scenario("restricting nothing is every row, and or-ing with it stays every row") {
                seeded { jpa ->
                    val (unrestricted, ored) =
                        jpa.session { session ->
                            session.select<Purchase>().where(nothing).count() to
                                session.select<Purchase>().where(large or nothing).count()
                        }

                    unrestricted shouldBe 3L
                    // every row `or` anything is still every row — not the half `large` would have kept
                    ored shouldBe 3L
                }
            }

            scenario("and keeps whichever side restricts anything") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.select<Purchase>().where(large and nothing).count() shouldBe 2L
                        session.select<Purchase>().where(large and adas).count() shouldBe 1L
                        session.select<Purchase>().where(nothing and nothing).count() shouldBe 3L
                    }
                }
            }
        }
    })
