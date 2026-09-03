package com.softistx.jpa.criteria

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaConfig
import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.SchemaMode
import com.softistx.jpa.entity.Buyer
import com.softistx.jpa.entity.Purchase
import com.softistx.jpa.entity.PurchaseLine
import com.softistx.jpa.entity.RepeatedPurchase
import com.softistx.jpa.query.query
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The foreign key read as a column, beside the association that owns it.
 *
 * `FetchJoinTest` pins the rule this bends: an unfetched `LAZY` association throws, and a query says
 * what it loads. That answer needs the query to know what will be read, and a GraphQL query does not
 * — the parents are loaded before anything knows whether the child field was selected. So the
 * DataLoader keys on the column instead, which is already in the owner's row.
 *
 * Two things have to be true for that to be worth recommending, and one thing has to be said out
 * loud. It must cost no statement, Hibernate must accept the duplicate mapping, and the scalar goes
 * **stale** the moment the association is reassigned — see the last scenario, which is the one that
 * costs an afternoon.
 */
class ForeignKeyColumnTest :
    FeatureSpec({

        suspend fun Jpa.seed() =
            transaction { session ->
                val ada = Buyer(1, "ada", "gold")
                val grace = Buyer(2, "grace", null)
                session.persist(ada, grace)
                session.persist(Purchase(1, "P-1", 100).also { it.customer = ada })
                session.persist(Purchase(2, "P-2", 200).also { it.customer = grace })
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
                        listOf(Buyer::class, Purchase::class, PurchaseLine::class),
                    ).use { jpa ->
                        jpa.seed()
                        block(jpa)
                    }
            }

        feature("a foreign key mapped a second time as a column").config(enabled = JpaTestDatabase.available) {
            scenario("builds, because the second mapping is read-only") {
                seeded { jpa -> jpa.isOpen shouldBe true }
            }

            scenario("and does not, when both mappings are writable") {
                // The mistake anyone reaching for the foreign key makes first. The message is
                // recorded rather than predicted, and `docs/jpa-mapping.md` quotes what it says.
                val failure =
                    shouldThrowAny {
                        JpaTestDatabase.withSchema { schema ->
                            Jpa
                                .connect(
                                    JpaConfig(
                                        uri = JpaTestDatabase.endpoint.uri,
                                        username = JpaTestDatabase.endpoint.username,
                                        password = JpaTestDatabase.endpoint.password,
                                        schema = schema,
                                        schemaMode = SchemaMode.CREATE_DROP,
                                    ),
                                    listOf(Buyer::class, RepeatedPurchase::class),
                                ).use { }
                        }
                    }

                // Hibernate names the remedy itself, which is worth pinning: the two flags are its
                // own advice and not a convention this repo invented.
                failure.message shouldContain "Column 'customer_id' is duplicated in mapping"
                failure.message shouldContain "use '@Column(insertable=false, updatable=false)'"
            }

            scenario("costs no secondary fetch, where reading the association throws") {
                seeded { jpa ->
                    // The value is already in the row that loaded the owner, so there is nothing to
                    // go back for. This is the whole claim, and it is the same instrument
                    // `FetchJoinTest` counts a fetch join with.
                    jpa.secondaryFetches { j ->
                        j.session { session ->
                            session.query<Purchase>("from Purchase").list().map { it.customerId }
                        }
                    } shouldBe 0L

                    shouldThrowAny {
                        jpa.session { session ->
                            session.query<Purchase>("from Purchase").list().map { it.customer?.name }
                        }
                    }
                }
            }

            scenario("reads the id the association points at") {
                seeded { jpa ->
                    jpa.session { session ->
                        session
                            .query<Purchase>("from Purchase order by id")
                            .list()
                            .map { it.customerId }
                    } shouldBe listOf(1L, 2L)
                }
            }
        }

        feature("what read-only costs").config(enabled = JpaTestDatabase.available) {
            scenario("the scalar is stale until the owner is loaded again") {
                // A read-only mapping is never written back, so the instance that changed the
                // association still reports the old key. Correct after a reload, wrong before —
                // and nothing warns.
                seeded { jpa ->
                    jpa.transaction { session ->
                        val purchase = session.get<Purchase>(1L)
                        purchase.customer = session.get<Buyer>(2L)
                        session.flush()

                        purchase.customerId shouldBe 1L
                    }

                    jpa.session { session -> session.get<Purchase>(1L).customerId } shouldBe 2L
                }
            }
        }
    })
