package com.strange.jpa.dsl

import com.strange.jpa.Jpa
import com.strange.jpa.JpaConfig
import com.strange.jpa.JpaMappingException
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.JpaPaginationException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.SchemaMode
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Plain
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.entity.StoredCrate
import com.strange.jpa.entity.Thing
import com.strange.jpa.entity.Warehouse
import com.strange.jpa.page.PageRequest
import com.strange.jpa.page.page
import com.strange.jpa.repository.JpaRepository
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.statelessSession
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * A fetch plan said once and applied wherever it is needed.
 *
 * The two things a fetch join cannot do are the reason this exists, so they lead: loading by
 * identifier, and nesting more than one level.
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
                            Thing::class,
                        ),
                    ).use { jpa ->
                        jpa.transaction { it.seed() }
                        block(jpa)
                    }
            }

        feature("a plan on a load by identifier").config(enabled = JpaTestDatabase.available) {
            // A fetch join needs a query to hang on, and `find` has none — so before this the only
            // way to read an association off a row you had the id of was to write a select instead.
            scenario("loads what it names, where a bare find leaves it unreadable") {
                seeded { jpa ->
                    val found =
                        jpa.session { session ->
                            session.find(1L, session.entityGraph<Purchase> { add(Purchase::customer) })
                        }
                    found!!.customer?.name shouldBe "ada"

                    val bare = jpa.session { session -> session.find<Purchase>(1L) }
                    shouldThrowAny { bare!!.customer?.name }
                }
            }

            scenario("says which row it did not find, like the find beside it") {
                seeded { jpa ->
                    jpa.session { session ->
                        val plan = session.entityGraph<Purchase> { add(Purchase::customer) }
                        session.find(99L, plan) shouldBe null
                        shouldThrow<JpaNotFoundException> { session.get(99L, plan) }
                            .message!! shouldContain "Purchase"
                    }
                }
            }

            // A stateless session has no persistence context, so nothing can be initialised after
            // the fact at all — the plan is the only way in.
            scenario("works on a stateless session, which has no second chance") {
                seeded { jpa ->
                    jpa
                        .statelessSession { session ->
                            session.get(1L, session.entityGraph<Purchase> { add(Purchase::customer) })
                        }.customer
                        ?.name shouldBe "ada"
                }
            }

            scenario("reaches the repository, which loads by id and cannot join") {
                seeded { jpa ->
                    val purchases = JpaRepository(Purchase::id)
                    jpa
                        .session { session ->
                            purchases.requireById(session, 1L, session.entityGraph { add(Purchase::customer) })
                        }.customer
                        ?.name shouldBe "ada"
                }
            }
        }

        feature("a plan on a query").config(enabled = JpaTestDatabase.available) {
            scenario("loads what it names, and costs no secondary fetch") {
                seeded { jpa ->
                    jpa.factory.statistics.clear()
                    val purchases =
                        jpa.session { session ->
                            session
                                .select<Purchase>()
                                .graph(session.entityGraph { add(Purchase::customer) })
                                .orderBy { asc(Purchase::id) }
                                .list()
                        }

                    purchases.map { it.customer?.name } shouldContainExactly listOf("ada", "bo", "ada")
                    jpa.factory.statistics.entityFetchCount shouldBe 0L
                }
            }

            // The depth a fetch join deliberately does not go to.
            scenario("nests as far as the mapping does, which a fetch join does not") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            session
                                .select<Purchase>()
                                .graph(
                                    session.entityGraph {
                                        subgraphEach(Purchase::lines) { add(PurchaseLine::purchase) }
                                    },
                                ).orderBy { asc(Purchase::id) }
                                .list()
                        }.first()
                        .lines
                        .map { it.purchase?.reference } shouldContainExactly listOf("P-1", "P-1", "P-1")
                }
            }

            scenario("outlives the session that built it, so one plan serves every request") {
                seeded { jpa ->
                    val plan = jpa.session { it.entityGraph<Purchase> { add(Purchase::customer) } }

                    repeat(2) {
                        jpa
                            .session { session -> session.select<Purchase>().graph(plan).list() }
                            .map { it.customer?.name }
                            .shouldContainExactly(listOf("ada", "bo", "ada"))
                    }
                }
            }

            // Hibernate applies `setPlan` with fetch-graph semantics, not load-graph: an EAGER
            // association the plan does not name becomes lazy for that query, and then throws. One
            // more reason this module says to mark every association LAZY and name what it needs.
            scenario("is a fetch graph, so it takes an unnamed eager association away") {
                seeded { jpa ->
                    val crates =
                        jpa.session { session ->
                            session
                                .select<StoredCrate>()
                                .graph(session.entityGraph { add(StoredCrate::code) })
                                .list()
                        }
                    shouldThrowAny { crates.first().warehouse?.name }

                    // Without the plan, the mapping's own EAGER still applies.
                    jpa
                        .session { session -> session.select<StoredCrate>().list() }
                        .first()
                        .warehouse
                        ?.name shouldBe "w-1"
                }
            }
        }

        feature("a plan that loads a collection").config(enabled = JpaTestDatabase.available) {
            scenario("loads it whole, and de-duplicates the owners") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            session
                                .select<Purchase>()
                                .graph(session.entityGraph { addEach(Purchase::lines) })
                                .orderBy { asc(Purchase::id) }
                                .list()
                        }.map { it.lines.size } shouldContainExactly listOf(3, 1, 0)
                }
            }

            // Measured the same way a fetchEach was: the database cuts the joined rows, not the
            // owners, so the page comes back short and its last row incomplete.
            scenario("refuses limit, offset and page, exactly as fetchEach does") {
                seeded { jpa ->
                    jpa.session { session ->
                        val plan = session.entityGraph<Purchase> { addEach(Purchase::lines) }

                        shouldThrow<JpaPaginationException> {
                            session.select<Purchase>().graph(plan).limit(2)
                        }.message!! shouldContain "part of its collection"

                        shouldThrow<JpaPaginationException> {
                            session.select<Purchase>().graph(plan).offset(1)
                        }

                        shouldThrow<JpaPaginationException> {
                            session
                                .select<Purchase>()
                                .graph(plan)
                                .sortBy(Purchase::id)
                                .page(PageRequest.first(2))
                        }
                    }
                }
            }

            scenario("and a plan of to-ones does not, since it multiplies no rows") {
                seeded { jpa ->
                    jpa
                        .session { session ->
                            session
                                .select<Purchase>()
                                .graph(session.entityGraph { add(Purchase::customer) })
                                .orderBy { asc(Purchase::id) }
                                .limit(2)
                                .list()
                        }.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                }
            }
        }

        // `add` and `addEach` are one JPA call and two names here so the plan knows whether it holds
        // a collection. KProperty1 is covariant, so `add(Purchase::lines)` type-checks — the mapping
        // is what refuses it, and without that the paging refusal above would be dodgeable.
        feature("what a plan refuses to be built out of") {
            scenario("a collection passed to add, since that is what makes a page unsafe") {
                seeded { jpa ->
                    jpa.session { session ->
                        shouldThrow<JpaMappingException> {
                            session.entityGraph<Purchase> { add(Purchase::lines) }
                        }.message!! shouldContain "addEach(Purchase::lines)"
                    }
                }
            }

            scenario("a to-one passed to addEach") {
                seeded { jpa ->
                    jpa.session { session ->
                        shouldThrow<JpaMappingException> {
                            @Suppress("UNCHECKED_CAST")
                            session.entityGraph<Purchase> {
                                addEach(Purchase::customer as kotlin.reflect.KProperty1<Purchase, Collection<*>>)
                            }
                        }.message!! shouldContain "add(Purchase::customer)"
                    }
                }
            }

            scenario("a class that is not an entity") {
                seeded { jpa ->
                    jpa.session { session ->
                        shouldThrow<JpaMappingException> { session.entityGraph<Plain> { } }
                    }
                }
            }
        }
    })
