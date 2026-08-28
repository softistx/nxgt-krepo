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

/** What a row is, when it is not the entity. */
class ProjectDslTest :
    FeatureSpec({

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            val bo = Buyer(2, "bo", null)
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

        feature("a projection").config(enabled = JpaTestDatabase.available) {
            scenario("of one column needs no constructor — a path is already a row") {
                seeded { jpa ->
                    val references =
                        jpa.session { session ->
                            session
                                .project<Purchase, String> {
                                    orderBy { asc(Purchase::id) }
                                    this[Purchase::reference]
                                }.list()
                        }

                    references shouldContainExactly listOf("P-1", "P-2", "P-3")
                }
            }

            scenario("builds a row from columns on both sides of a join") {
                seeded { jpa ->
                    val summaries =
                        jpa.session { session ->
                            session
                                .project<Purchase, Summary> {
                                    val buyer = join(Purchase::customer)
                                    where { Purchase::total gt 60L }
                                    orderBy { asc(Purchase::id) }
                                    construct(::Summary, Purchase::reference, buyer[Buyer::name])
                                }.list()
                        }

                    summaries.map { it.reference to it.buyer } shouldContainExactly
                        listOf("P-1" to "ada", "P-3" to "bo")
                }
            }

            scenario("names its columns by property, and by path only where a property cannot reach") {
                seeded { jpa ->
                    val rows =
                        jpa.session { session ->
                            session
                                .project<Purchase, Line> {
                                    val buyer = join(Purchase::customer)
                                    orderBy { asc(Purchase::id) }
                                    // two of the entity's own columns, one from the join, one
                                    // computed — the property form for what it reaches, a path for
                                    // the rest, in one call
                                    construct(
                                        ::Line,
                                        Purchase::reference,
                                        Purchase::total,
                                        buyer[Buyer::name],
                                        upper(this[Purchase::reference]),
                                    )
                                }.list()
                        }

                    rows.map { it.reference to it.buyer } shouldContainExactly
                        listOf("P-1" to "ada", "P-2" to "ada", "P-3" to "bo")
                    rows.map { it.shouted } shouldContainExactly listOf("P-1", "P-2", "P-3")
                    rows.map { it.total } shouldContainExactly listOf(150L, 50L, 400L)
                }
            }

            // The mixtures stop at four columns; above it every column is a path. Pinned because
            // the escape is the whole answer to the overload-resolution error a fifth column gives.
            scenario("above four columns, names every column by path") {
                seeded { jpa ->
                    val rows =
                        jpa.session { session ->
                            session
                                .project<Purchase, Wide> {
                                    val buyer = join(Purchase::customer)
                                    orderBy { asc(Purchase::id) }
                                    construct(
                                        ::Wide,
                                        this[Purchase::id],
                                        this[Purchase::reference],
                                        this[Purchase::total],
                                        buyer[Buyer::name],
                                        upper(this[Purchase::reference]),
                                    )
                                }.list()
                        }

                    rows.map { it.id } shouldContainExactly listOf(1L, 2L, 3L)
                    rows.map { it.buyer } shouldContainExactly listOf("ada", "ada", "bo")
                    rows.map { it.shouted } shouldContainExactly listOf("P-1", "P-2", "P-3")
                }
            }

            scenario("carries the paging and the terminals every other query has") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .project<Purchase, String> {
                                orderBy { asc(Purchase::id) }
                                this[Purchase::reference]
                            }.limit(1)
                            .offset(1)
                            .list()
                    } shouldContainExactly listOf("P-2")

                    jpa.session { session ->
                        session
                            .project<Purchase, String> { this[Purchase::reference] }
                            .count()
                    } shouldBe 3L
                }
            }

            scenario("groups, and restricts the groups after they are formed") {
                seeded { jpa ->
                    val perBuyer =
                        jpa.session { session ->
                            session
                                .project<Purchase, Tally> {
                                    val buyer = join(Purchase::customer)
                                    groupBy { buyer[Buyer::name] }
                                    having { count(this[Purchase::id]) gt 1L }
                                    construct(::Tally, buyer[Buyer::name], count(this[Purchase::id]))
                                }.list()
                        }

                    perBuyer.map { it.buyer to it.purchases } shouldContainExactly listOf("ada" to 2L)
                }
            }

            scenario("reads only the columns it names, so a scalar comes back a scalar") {
                seeded { jpa ->
                    val totals =
                        jpa.session { session ->
                            session
                                .project<Purchase, Long> {
                                    where { Purchase::total gt 100L }
                                    orderBy { asc(Purchase::total) }
                                    this[Purchase::total]
                                }.list()
                        }

                    totals shouldContainExactly listOf(150L, 400L)
                }
            }
        }
    })

/** A row of two columns, one from each side of the join. */
class Summary(
    val reference: String,
    val buyer: String,
)

/** A row of four columns: two of the entity's own, one joined, one computed. */
class Line(
    val reference: String,
    val total: Long,
    val buyer: String,
    val shouted: String,
)

/** A row of five columns — above where the property/path mixtures stop. */
class Wide(
    val id: Long,
    val reference: String,
    val total: Long,
    val buyer: String,
    val shouted: String,
)

/** A row of a group and its count. */
class Tally(
    val buyer: String,
    val purchases: Long,
)
