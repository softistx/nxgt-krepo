package com.strange.jpa.criteria

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.SchemaMode
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.entity.StoredCrate
import com.strange.jpa.entity.Warehouse
import com.strange.jpa.query.query
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.createQuery
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import jakarta.persistence.criteria.JoinType

/**
 * Loading an association with its owner rather than in a query per row.
 *
 * The numbers here are the reason the feature exists, so they are measured rather than described:
 * every scenario about cost reads Hibernate's own statistics.
 */
class FetchJoinTest :
    FeatureSpec({

        // Three purchases, three *different* buyers — the shape that makes N+1 visible — and one
        // purchase with no lines at all, which is what tells a left fetch from an inner one.
        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            val bo = Buyer(2, "bo", null)
            val cy = Buyer(3, "cy", "silver")
            persist(ada, bo, cy)
            val first = Purchase(1, "P-1", 150, ada)
            val second = Purchase(2, "P-2", 50, bo)
            val third = Purchase(3, "P-3", 400, cy)
            persist(first, second, third)
            persist(
                PurchaseLine(1, "apples", first),
                PurchaseLine(2, "pears", first),
                PurchaseLine(3, "figs", first),
                PurchaseLine(4, "plums", second),
            )

            // Three crates in three different warehouses, over the one association in the test tree
            // left at JPA's eager default.
            (1L..3L).forEach { n ->
                val warehouse = Warehouse(n, "w-$n")
                persist(warehouse)
                persist(StoredCrate(n, "C-$n", warehouse))
            }
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
                            // The only honest witness to N+1: a spec that asserts the values came
                            // back asserts nothing about how many round trips it took to get them.
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

        /**
         * How many entities [block] made Hibernate go back for — the N+1, counted.
         *
         * `prepareStatementCount` is the obvious counter and reads zero: it is a JDBC metric and
         * there is no JDBC under the Vert.x pool. `entityFetchCount` counts the loads a query did
         * not ask for, which is the number this feature exists to move.
         */
        suspend fun <T> Jpa.secondaryFetches(block: suspend (Jpa) -> T): Long {
            factory.statistics.clear()
            block(this)
            return factory.statistics.entityFetchCount
        }

        feature("a to-one fetch").config(enabled = JpaTestDatabase.available) {
            scenario("costs no secondary fetch, where the eager default costs one per distinct owner") {
                seeded { jpa ->
                    jpa.secondaryFetches { j ->
                        j.session { session ->
                            session.query<StoredCrate>("from StoredCrate").list().map { it.warehouse?.name }
                        }
                    } shouldBe 3L

                    jpa.secondaryFetches { j ->
                        j.session { session ->
                            val criteria = session.createQuery<StoredCrate>()
                            criteria.from(StoredCrate::class.java).fetch(StoredCrate::warehouse)

                            session.query(criteria).list().map { it.warehouse?.name }
                        }
                    } shouldBe 0L
                }
            }

            // Hibernate Reactive has no transparent lazy loading at all — there is no thread to
            // block on the second select — so a lazy association is not "slow to read", it is
            // unreadable. That is the strongest argument for saying what to load in the query.
            scenario("is the only way to read a lazy association, inside the session as well as after") {
                seeded { jpa ->
                    shouldThrowAny {
                        jpa.session { session ->
                            session.query<Purchase>("from Purchase").list().map { it.customer?.name }
                        }
                    }

                    jpa
                        .session { session ->
                            val criteria = session.createQuery<Purchase>()
                            criteria.from(Purchase::class.java).fetch(Purchase::customer)

                            session.query(criteria).list()
                        }.map { it.customer?.name } shouldContainExactly listOf("ada", "bo", "cy")
                }
            }

            scenario("does not multiply rows, so a limit still means owners") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            val criteria = session.createQuery<Purchase>()
                            val purchase = criteria.from(Purchase::class.java)
                            purchase.fetch(Purchase::customer)
                            criteria.orderBy(asc(purchase[Purchase::id]))

                            session.query(criteria).limit(2).list()
                        }.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                }
            }

            scenario("answers with a join, so the fetched association filters and orders like any other") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            val criteria = session.createQuery<Purchase>()
                            val purchase = criteria.from(Purchase::class.java)
                            val buyer = purchase.fetch(Purchase::customer)

                            criteria.where(buyer[Buyer::tier].isNotNull())
                            criteria.orderBy(desc(buyer[Buyer::name]))

                            session.query(criteria).list()
                        }.map { it.reference } shouldContainExactly listOf("P-3", "P-1")
                }
            }
        }

        feature("a collection fetch").config(enabled = JpaTestDatabase.available) {
            scenario("loads the whole collection in the same statement") {
                seeded { jpa ->
                    val purchases =
                        jpa.session { session ->
                            val criteria = session.createQuery<Purchase>()
                            val purchase = criteria.from(Purchase::class.java)
                            purchase.fetchEach(Purchase::lines)
                            criteria.orderBy(asc(purchase[Purchase::id]))

                            session.query(criteria).list()
                        }

                    // Read after the session: the collections are already there.
                    purchases.map { it.lines.size } shouldContainExactly listOf(3, 1, 0)
                }
            }

            scenario("de-duplicates the owners, so distinct is not needed after it") {
                seeded { jpa ->
                    jpa.session { session ->
                        val criteria = session.createQuery<Purchase>()
                        criteria.from(Purchase::class.java).fetchEach(Purchase::lines)

                        session.query(criteria).list().size
                    } shouldBe 3

                    // And so does a plain join to the same association: on Hibernate 7 an entity
                    // query de-duplicates its rows whether the join fetches or not. The duplication
                    // is still there in the SQL — a projection over the same join sees all five.
                    jpa.session { session ->
                        val criteria = session.createQuery<Purchase>()
                        criteria.from(Purchase::class.java).joinEach(Purchase::lines, JoinType.LEFT)

                        session.query(criteria).list().size
                    } shouldBe 3

                    jpa.session { session ->
                        val criteria = session.createQuery<String>()
                        val purchase = criteria.from(Purchase::class.java)
                        purchase.joinEach(Purchase::lines, JoinType.LEFT)
                        criteria.select(purchase[Purchase::reference])

                        session.query(criteria).list().size
                    } shouldBe 5
                }
            }

            scenario("is a left join by default, so an owner with no children is kept") {
                seeded { jpa ->
                    suspend fun references(type: JoinType) =
                        jpa
                            .session { session ->
                                val criteria = session.createQuery<Purchase>()
                                val purchase = criteria.from(Purchase::class.java)
                                purchase.fetchEach(Purchase::lines, type)
                                criteria.orderBy(asc(purchase[Purchase::id]))

                                session.query(criteria).list()
                            }.map { it.reference }

                    references(JoinType.LEFT) shouldContainExactly listOf("P-1", "P-2", "P-3")
                    references(JoinType.INNER) shouldContainExactly listOf("P-1", "P-2")
                }
            }

            // The hazard the KDoc warns about, pinned so it cannot quietly change: nothing refuses
            // this combination, and what comes back is one owner holding two of its three lines.
            scenario("truncates under a limit, because the limit applies to the joined rows") {
                seeded { jpa ->
                    val purchases =
                        jpa.session { session ->
                            val criteria = session.createQuery<Purchase>()
                            val purchase = criteria.from(Purchase::class.java)
                            purchase.fetchEach(Purchase::lines)
                            criteria.orderBy(asc(purchase[Purchase::id]))

                            session.query(criteria).limit(2).list()
                        }

                    purchases.map { it.reference } shouldContainExactly listOf("P-1")
                    purchases.single().lines.size shouldBe 2
                }
            }
        }
    })
