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
                                orderBy { asc(Buyer::id) }
                                concat(upper(this[Buyer::name]), "!")
                            }.list()
                    } shouldContainExactly listOf("ADA!", "BO!")

                    jpa.session { session ->
                        session
                            .project<Buyer, String> {
                                orderBy { asc(Buyer::id) }
                                coalesce(this[Buyer::tier], "none")
                            }.list()
                    } shouldContainExactly listOf("gold", "none")
                }
            }

            // The rest of the vocabulary the README advertises, in one scenario rather than five:
            // each is one call into the criteria builder, and what a spec is for here is that the
            // call is the right one and the argument types line up.
            scenario("cover the rest of what the README advertises") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Purchase, String> {
                                orderBy { asc(Purchase::id) }
                                substring(this[Purchase::reference], 3)
                            }.list()
                    } shouldContainExactly listOf("1", "2", "3")

                    jpa.session { session ->
                        session
                            .project<Purchase, String> {
                                orderBy { asc(Purchase::id) }
                                substring(this[Purchase::reference], 1, 1)
                            }.list()
                    } shouldContainExactly listOf("P", "P", "P")

                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                orderBy { asc(Purchase::id) }
                                abs(this[Purchase::total] - 200L)
                            }.list()
                    } shouldContainExactly listOf(50L, 150L, 200L)

                    jpa
                        .session { session ->
                            session
                                .project<Purchase, Double> {
                                    orderBy { asc(Purchase::id) }
                                    sqrt(this[Purchase::total])
                                }.list()
                        }.map { it.toInt() } shouldContainExactly listOf(12, 7, 20)

                    // `mod` takes an Int expression, which is what `length` answers with.
                    jpa.session { session ->
                        session
                            .project<Purchase, Int> {
                                orderBy { asc(Purchase::id) }
                                mod(length(this[Purchase::reference]), 2)
                            }.list()
                    } shouldContainExactly listOf(1, 1, 1)
                }
            }

            scenario("nullIf turns a value into a null, which is what coalesce reads back") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Buyer, String> {
                                orderBy { asc(Buyer::id) }
                                coalesce(nullIf(this[Buyer::name], "Ada"), "hidden")
                            }.list()
                    } shouldContainExactly listOf("hidden", "Bo")
                }
            }
        }

        feature("arithmetic on a column").config(enabled = JpaTestDatabase.available) {
            scenario("takes a literal on the right, and answers the column's own type") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                orderBy { asc(Purchase::id) }
                                this[Purchase::total] + 10L
                            }.list()
                    } shouldContainExactly listOf(160L, 60L, 410L)

                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                orderBy { asc(Purchase::id) }
                                this[Purchase::total] - 10L
                            }.list()
                    } shouldContainExactly listOf(140L, 40L, 390L)

                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                orderBy { asc(Purchase::id) }
                                this[Purchase::total] * 2L
                            }.list()
                    } shouldContainExactly listOf(300L, 100L, 800L)
                }
            }

            scenario("takes another column on the right") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                orderBy { asc(Purchase::id) }
                                this[Purchase::total] + this[Purchase::id]
                            }.list()
                    } shouldContainExactly listOf(151L, 52L, 403L)

                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                orderBy { asc(Purchase::id) }
                                this[Purchase::total] - this[Purchase::id]
                            }.list()
                    } shouldContainExactly listOf(149L, 48L, 397L)

                    jpa.session { session ->
                        session
                            .project<Purchase, Long> {
                                orderBy { asc(Purchase::id) }
                                this[Purchase::total] * this[Purchase::id]
                            }.list()
                    } shouldContainExactly listOf(150L, 100L, 1200L)
                }
            }

            // Division is the one operation whose result is not the operands' type, which is JPA's
            // signature and the reason an assignment that wants a column back has to say which.
            scenario("widens when it divides, both against a literal and against a column") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            session
                                .project<Purchase, Number> {
                                    orderBy { asc(Purchase::id) }
                                    this[Purchase::total] / 50
                                }.list()
                        }.map { it.toLong() } shouldContainExactly listOf(3L, 1L, 8L)

                    jpa
                        .session { session ->
                            session
                                .project<Purchase, Number> {
                                    orderBy { asc(Purchase::id) }
                                    this[Purchase::total] / this[Purchase::id]
                                }.list()
                        }.map { it.toLong() } shouldContainExactly listOf(150L, 25L, 133L)
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
                                orderBy { asc(Buyer::id) }
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
                                    orderBy { asc(Buyer::id) }
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
