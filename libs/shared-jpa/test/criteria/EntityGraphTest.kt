package com.strange.jpa.criteria

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.SchemaMode
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.entity.StoredCrate
import com.strange.jpa.entity.Warehouse
import com.strange.jpa.query.query
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.entityGraph
import com.strange.jpa.session.session
import com.strange.jpa.session.statelessSession
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityGraph

/**
 * A fetch plan said once and applied wherever it is needed.
 *
 * The two things a fetch join cannot do are the reason this exists, so they lead: loading by
 * identifier, and outliving the query that wanted it.
 */
class EntityGraphTest :
    FeatureSpec({

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            val bo = Buyer(2, "bo", null)
            persist(ada, bo)
            val first = Purchase(1, "P-1", 150, ada)
            val second = Purchase(2, "P-2", 50, bo)
            val third = Purchase(3, "P-3", 400, ada)
            persist(first, second, third)
            persist(
                PurchaseLine(1, "apples", first),
                PurchaseLine(2, "pears", first),
                PurchaseLine(3, "figs", first),
                PurchaseLine(4, "plums", second),
            )
            val warehouse = Warehouse(1, "w-1")
            persist(warehouse)
            persist(StoredCrate(1, "C-1", warehouse))
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withSchema { schema ->
                Jpa
                    .connect(
                        JpaConfig(
                            uri = JpaTestDatabase.endpoint.uri,
                            username = JpaTestDatabase.endpoint.username,
                            password = JpaTestDatabase.endpoint.password,
                            schema = schema,
                            schemaMode = SchemaMode.CREATE_DROP,
                            properties = mapOf("hibernate.generate_statistics" to "true"),
                        ),
                        listOf(
                            Buyer::class,
                            Purchase::class,
                            PurchaseLine::class,
                            Warehouse::class,
                            StoredCrate::class,
                        ),
                    ).use { jpa ->
                        jpa.transaction { it.seed() }
                        block(jpa)
                    }
            }

        feature("a plan on a load by identifier").config(enabled = JpaTestDatabase.available) {
            // The thing a fetch join cannot do at all: `find` takes no query to hang one off.
            scenario("loads what it names, where a bare find leaves it unreadable") {
                seeded { jpa ->
                    shouldThrowAny {
                        jpa.session { session -> session.get<Purchase>(1L).customer?.name }
                    }

                    jpa.session { session ->
                        val plan = session.entityGraph<Purchase>().add(Purchase::customer)
                        session.find(1L, plan)?.customer?.name
                    } shouldBe "ada"
                }
            }

            scenario("says which row it did not find, like the find beside it") {
                seeded { jpa ->
                    shouldThrow<JpaNotFoundException> {
                        jpa.session { session ->
                            session.get<Purchase>(99L, session.entityGraph<Purchase>().add(Purchase::customer))
                        }
                    }.id shouldBe 99L
                }
            }

            scenario("works on a stateless session, which has no second chance at all") {
                seeded { jpa ->
                    jpa.statelessSession { session ->
                        val plan = session.entityGraph<Purchase>().add(Purchase::customer)
                        session.get<Purchase>(1L, plan).customer?.name
                    } shouldBe "ada"
                }
            }
        }

        feature("a plan on a query").config(enabled = JpaTestDatabase.available) {
            scenario("loads what it names, and costs no secondary fetch") {
                seeded { jpa ->
                    jpa.factory.statistics.clear()

                    jpa
                        .session { session ->
                            val plan = session.entityGraph<StoredCrate>().add(StoredCrate::warehouse)
                            session.query<StoredCrate>("from StoredCrate").plan(plan).list()
                        }.map { it.warehouse?.name } shouldContainExactly listOf("w-1")

                    jpa.factory.statistics.entityFetchCount shouldBe 0L
                }
            }

            scenario("nests as far as the mapping does, which a fetch join stops short of") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            // Each step returns the level it just made, so a plan two deep is built
                            // by descending rather than by nesting lambdas: a line, the purchase it
                            // belongs to, and that purchase's buyer, all in one statement.
                            val plan = session.entityGraph<PurchaseLine>()
                            plan
                                .subgraphOf(PurchaseLine::purchase)
                                .add(Purchase::customer)

                            session.query<PurchaseLine>("from PurchaseLine where id = 1").plan(plan).list()
                        }.single()
                        .purchase
                        ?.customer
                        ?.name shouldBe "ada"
                }
            }

            scenario("outlives the session that built it, so one plan serves every request") {
                seeded { jpa ->
                    val plan: EntityGraph<Purchase> =
                        jpa.session { session -> session.entityGraph<Purchase>().add(Purchase::customer) }

                    // A different session, a different unit of work, the same value.
                    jpa
                        .session { session ->
                            session.query<Purchase>("from Purchase order by id").plan(plan).list()
                        }.map { it.customer?.name } shouldContainExactly listOf("ada", "bo", "ada")
                }
            }

            // `setPlan` applies *fetch-graph* semantics: what the plan does not name is lazy for
            // this query, eager mapping or not — and under Hibernate Reactive lazy means unreadable.
            scenario("is a fetch graph, so it takes an unnamed eager association away") {
                seeded { jpa ->
                    shouldThrowAny {
                        jpa.session { session ->
                            val plan = session.entityGraph<StoredCrate>()
                            session
                                .query<StoredCrate>("from StoredCrate")
                                .plan(plan)
                                .list()
                                .map { it.warehouse?.name }
                        }
                    }
                }
            }
        }

        feature("a plan that loads a collection").config(enabled = JpaTestDatabase.available) {
            scenario("loads it whole, and de-duplicates the owners") {
                seeded { jpa ->
                    val purchases =
                        jpa.session { session ->
                            val plan = session.entityGraph<Purchase>().add(Purchase::lines)
                            session.query<Purchase>("from Purchase order by id").plan(plan).list()
                        }

                    purchases.map { it.reference } shouldContainExactly listOf("P-1", "P-2", "P-3")
                    purchases.map { it.lines.size } shouldContainExactly listOf(3, 1, 0)
                }
            }

            // The same truncation a collection fetch join produces, and for the same reason: the
            // limit is applied to the joined rows. Pinned so that it cannot change unnoticed.
            scenario("truncates under a limit, exactly as fetchEach does") {
                seeded { jpa ->
                    val purchases =
                        jpa.session { session ->
                            val plan = session.entityGraph<Purchase>().add(Purchase::lines)
                            session
                                .query<Purchase>("from Purchase order by id")
                                .plan(plan)
                                .limit(2)
                                .list()
                        }

                    purchases.map { it.reference } shouldContainExactly listOf("P-1")
                    purchases.single().lines.size shouldBe 2
                }
            }

            scenario("and a plan of to-ones does not, since it multiplies no rows") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            val plan = session.entityGraph<Purchase>().add(Purchase::customer)
                            session
                                .query<Purchase>("from Purchase order by id")
                                .plan(plan)
                                .limit(2)
                                .list()
                        }.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                }
            }
        }
    })
