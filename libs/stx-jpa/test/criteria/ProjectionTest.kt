package com.softistx.jpa.criteria

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.entity.Buyer
import com.softistx.jpa.entity.Purchase
import com.softistx.jpa.entity.PurchaseLine
import com.softistx.jpa.query.query
import com.softistx.jpa.session.JpaSession
import com.softistx.jpa.session.createQuery
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/** Three columns and a constructor to take them — what a Java record would be here. */
data class PurchaseSummary(
    val reference: String,
    val total: Long,
    val buyer: String,
)

/** One column and a count, for a grouped query. */
data class BuyerTally(
    val name: String,
    val purchases: Long,
)

/**
 * Reading part of an entity, by handing the query a class to package the rows into.
 *
 * Hibernate 6 and later take an arbitrary result class with a matching constructor and build the
 * selection list into it — no `select new com.…Summary(…)` in the HQL, no constructor expression, no
 * `Tuple` to unpack. That is the whole projection story here, and it works the same whether the
 * query was written in HQL or built as a criteria.
 */
class ProjectionTest :
    FeatureSpec({

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            val bo = Buyer(2, "bo", null)
            persist(ada, bo)
            val first = Purchase(1, "P-1", 150, ada)
            val second = Purchase(2, "P-2", 50, bo)
            val third = Purchase(3, "P-3", 400, ada)
            persist(first, second, third)
            persist(PurchaseLine(1, "apples", first))
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { it.seed() }
                block(jpa)
            }

        feature("a result class on an HQL query").config(enabled = JpaTestDatabase.available) {
            scenario("packages the selected columns by position, with nothing said in the HQL") {
                seeded { jpa ->
                    val summaries =
                        jpa.session { session ->
                            session
                                .query<PurchaseSummary>(
                                    "select p.reference, p.total, p.customer.name from Purchase p order by p.id",
                                ).list()
                        }

                    summaries.map { it.reference } shouldContainExactly listOf("P-1", "P-2", "P-3")
                    summaries.first() shouldBe PurchaseSummary("P-1", 150, "ada")
                }
            }

            scenario("does the same for an aggregate, which is a column like any other") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            session
                                .query<BuyerTally>(
                                    "select b.name, count(p) from Purchase p join p.customer b " +
                                        "group by b.name order by count(p) desc",
                                ).list()
                        } shouldContainExactly listOf(BuyerTally("ada", 2), BuyerTally("bo", 1))
                }
            }

            scenario("and a single column needs no class at all") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.query<Long>("select count(p) from Purchase p").single()
                    } shouldBe 3L
                }
            }
        }

        feature("a result class on a criteria").config(enabled = JpaTestDatabase.available) {
            scenario("is the query's own type, so the selection is matched against it") {
                seeded { jpa ->
                    val summaries =
                        jpa.session { session ->
                            val criteria = session.createQuery<PurchaseSummary>()
                            val purchase = criteria.from(Purchase::class.java)
                            val buyer = purchase.join(Purchase::customer)

                            criteria.multiselect(
                                purchase[Purchase::reference],
                                purchase[Purchase::total],
                                buyer[Buyer::name],
                            )
                            criteria.orderBy(asc(purchase[Purchase::id]))

                            session.query(criteria).list()
                        }

                    summaries shouldContainExactly
                        listOf(
                            PurchaseSummary("P-1", 150, "ada"),
                            PurchaseSummary("P-2", 50, "bo"),
                            PurchaseSummary("P-3", 400, "ada"),
                        )
                }
            }

            scenario("groups and counts into it too") {
                seeded { jpa ->
                    jpa.session { session ->
                        val criteria = session.createQuery<BuyerTally>()
                        val purchase = criteria.from(Purchase::class.java)
                        val buyer = purchase.join(Purchase::customer)

                        criteria.multiselect(buyer[Buyer::name], count(purchase[Purchase::id]))
                        criteria.groupBy(buyer[Buyer::name])
                        criteria.having(count(purchase[Purchase::id]) gt 1L)

                        session.query(criteria).list()
                    } shouldContainExactly listOf(BuyerTally("ada", 2))
                }
            }
        }
    })
