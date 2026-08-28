package com.strange.jpa.query

import com.strange.jpa.Jpa
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.dsl.get
import com.strange.jpa.dsl.joinEach
import com.strange.jpa.dsl.oneOf
import com.strange.jpa.dsl.select
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The way out of the DSL: a criteria written against Hibernate's own API, run through this module's
 * terminals — and named by properties rather than by strings on the way.
 */
class CriteriaTest :
    FeatureSpec({

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            val bo = Buyer(2, "bo", null)
            persist(ada, bo)
            val first = Purchase(1, "P-1", 150, ada)
            val second = Purchase(2, "P-2", 50, bo)
            persist(first, second)
            persist(
                PurchaseLine(1, "apples", first),
                PurchaseLine(2, "pears", first),
                PurchaseLine(3, "plums", second),
            )
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { it.seed() }
                block(jpa)
            }

        feature("a criteria written by hand").config(enabled = JpaTestDatabase.available) {
            scenario("selects a join, and reaches the suspending terminals") {
                seeded { jpa ->
                    val lines =
                        jpa.session { session ->
                            // The shape Hibernate's own documentation uses, in Kotlin: a query whose
                            // root is one entity and whose result is a join off it.
                            val criteria = session.criteria.createQuery(PurchaseLine::class.java)
                            val purchase = criteria.from(Purchase::class.java)
                            val line = purchase.joinEach(Purchase::lines)

                            criteria.where(purchase[Purchase::reference] oneOf listOf("P-1", "P-9"))
                            criteria.select(line)

                            session.query(criteria).list()
                        }

                    lines.map { it.sku }.sorted() shouldContainExactly listOf("apples", "pears")
                }
            }

            scenario("names its attributes by property, across a to-one association") {
                seeded { jpa ->
                    val references =
                        jpa.session { session ->
                            val criteria = session.criteria.createQuery(String::class.java)
                            val purchase = criteria.from(Purchase::class.java)

                            // Chained, so Criteria joins implicitly — and `Purchase_.customer` and
                            // `Buyer_.name` are what this would have said with a static metamodel.
                            criteria.where(purchase[Purchase::customer][Buyer::name] oneOf listOf("ada"))
                            criteria.select(purchase[Purchase::reference])

                            session.query(criteria).list()
                        }

                    references shouldContainExactly listOf("P-1")
                }
            }

            scenario("returns what the DSL returns, being the same query underneath") {
                seeded { jpa ->
                    val (byHand, byDsl) =
                        jpa.session { session ->
                            val criteria = session.criteria.createQuery(Purchase::class.java)
                            val purchase = criteria.from(Purchase::class.java)
                            criteria.where(session.criteria.greaterThan(purchase[Purchase::total], 100L))

                            session.query(criteria).list().map { it.reference } to
                                session
                                    .select<Purchase>()
                                    .where { Purchase::total gt 100L }
                                    .list()
                                    .map { it.reference }
                        }

                    byHand shouldContainExactly byDsl
                }
            }
        }

        feature("a criteria that writes").config(enabled = JpaTestDatabase.available) {
            scenario("updates through the same mutation terminal") {
                seeded { jpa ->
                    val touched =
                        jpa.transaction { session ->
                            val statement = session.criteria.createCriteriaUpdate(Purchase::class.java)
                            val purchase = statement.from(Purchase::class.java)
                            statement.set(purchase[Purchase::total], 1L)
                            statement.where(session.criteria.lessThan(purchase[Purchase::total], 100L))

                            session.mutate(statement).execute()
                        }

                    touched shouldBe 1
                    jpa.session { it.select<Purchase>().where { Purchase::total eq 1L }.count() } shouldBe 1L
                }
            }

            scenario("deletes through it too") {
                seeded { jpa ->
                    val removed =
                        jpa.transaction { session ->
                            val statement = session.criteria.createCriteriaDelete(PurchaseLine::class.java)
                            val line = statement.from(PurchaseLine::class.java)
                            statement.where(line[PurchaseLine::sku] oneOf listOf("plums"))

                            session.mutate(statement).execute()
                        }

                    removed shouldBe 1
                    jpa.session { it.select<PurchaseLine>().count() } shouldBe 2L
                }
            }

            scenario("inserts from a select, which the DSL has no spelling for at all") {
                seeded { jpa ->
                    val inserted =
                        jpa.transaction { session ->
                            val statement = session.criteria.createCriteriaInsertSelect(Buyer::class.java)
                            statement.setInsertionTargetPaths(
                                statement.target[Buyer::id],
                                statement.target[Buyer::name],
                            )

                            val source = session.criteria.createTupleQuery()
                            val existing = source.from(Buyer::class.java)
                            source.multiselect(
                                session.criteria.sum(existing[Buyer::id], 10L),
                                existing[Buyer::name],
                            )
                            statement.select(source)

                            session.mutate(statement).execute()
                        }

                    inserted shouldBe 2
                    jpa.session { it.select<Buyer>().count() } shouldBe 4L
                }
            }
        }
    })
