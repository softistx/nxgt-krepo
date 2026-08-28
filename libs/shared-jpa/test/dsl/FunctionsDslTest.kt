package com.strange.jpa.dsl

import com.strange.jpa.Jpa
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The function vocabulary, the generic escape, and the SQL one under it. */
class FunctionsDslTest :
    FeatureSpec({

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "Ada", "gold")
            val bo = Buyer(2, "Bo", null)
            persist(ada, bo)
            persist(
                Purchase(1, "P-1", 150, ada),
                Purchase(2, "P-2", 50, ada),
                Purchase(3, "P-3", 400, bo),
            )
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { it.seed() }
                block(jpa)
            }

        feature("the named functions").config(enabled = JpaTestDatabase.available) {
            scenario("apply to a restriction, and compose") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            session
                                .select<Buyer> {
                                    where { lower(this[Buyer::name]) eq "ada" }
                                }.list()
                        }.map { it.name } shouldContainExactly listOf("Ada")

                    jpa
                        .session { session ->
                            session
                                .select<Buyer> {
                                    where { length(trim(this[Buyer::name])) eq 2 }
                                }.list()
                        }.map { it.name } shouldContainExactly listOf("Bo")
                }
            }

            scenario("apply to a projection, and to a nullable column") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Buyer, String> {
                                orderBy { asc(this[Buyer::id]) }
                                concat(upper(this[Buyer::name]), "!")
                            }.list()
                    } shouldContainExactly listOf("ADA!", "BO!")

                    jpa.session { session ->
                        session
                            .project<Buyer, String> {
                                orderBy { asc(this[Buyer::id]) }
                                coalesce(this[Buyer::tier], "none")
                            }.list()
                    } shouldContainExactly listOf("gold", "none")
                }
            }
        }

        feature("the aggregates").config(enabled = JpaTestDatabase.available) {
            scenario("count, sum, min and max over the whole result") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.project<Purchase, Long> { sum(this[Purchase::total]) }.single()
                    } shouldBe 600L

                    jpa.session { session ->
                        session.project<Purchase, Long> { max(this[Purchase::total]) }.single()
                    } shouldBe 400L

                    jpa.session { session ->
                        session.project<Purchase, Double> { avg(this[Purchase::total]) }.single()
                    } shouldBeGreaterThan 199.0
                }
            }

            scenario("countDistinct counts values, not rows") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                val buyer = join(Purchase::customer)
                                countDistinct(buyer[Buyer::id])
                            }.single()
                    } shouldBe 2L
                }
            }
        }

        feature("the generic escape").config(enabled = JpaTestDatabase.available) {
            scenario("calls a function by name") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Buyer, String> {
                                orderBy { asc(this[Buyer::id]) }
                                function<String>("upper", this[Buyer::name])
                            }.list()
                    } shouldContainExactly listOf("ADA", "BO")
                }
            }

            scenario("drops a SQL fragment in, with its arguments bound rather than pasted") {
                seeded { jpa ->
                    // The apostrophe is the point: a `?` that were a hole to interpolate into would
                    // end the string literal here and the statement would not parse.
                    val greeted =
                        jpa.session { session ->
                            session
                                .project<Buyer, String> {
                                    orderBy { asc(this[Buyer::id]) }
                                    sql<String>("? || ?", this[Buyer::name], literal("'s cart"))
                                }.list()
                        }

                    greeted shouldContainExactly listOf("Ada's cart", "Bo's cart")
                }
            }

            scenario("reaches what HQL has no syntax for") {
                seeded { jpa ->
                    val loud =
                        jpa.session { session ->
                            session
                                .select<Buyer> {
                                    where { sql<Boolean>("? ~ ?", this[Buyer::name], literal("^A")) eq true }
                                }.list()
                        }

                    loud.map { it.name } shouldContainExactly listOf("Ada")
                }
            }

            scenario("refuses a fragment whose placeholders and arguments disagree") {
                seeded { jpa ->
                    val refused =
                        shouldThrow<IllegalStateException> {
                            jpa.session { session ->
                                session
                                    .project<Buyer, String> {
                                        sql<String>("? || ?", this[Buyer::name])
                                    }.list()
                            }
                        }

                    refused.message shouldContain "2 placeholders and 1 arguments"
                }
            }
        }
    })
