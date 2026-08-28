package com.strange.jpa.repository

import com.strange.jpa.Jpa
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.dsl.eq
import com.strange.jpa.dsl.get
import com.strange.jpa.dsl.gt
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.page.PageRequest
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** One entity as an object: that it says what the DSL says, and takes its unit of work per call. */
class JpaRepositoryTest :
    FeatureSpec({

        val purchases = jpaRepository(Purchase::id)

        suspend fun JpaSession.seed() {
            val ada = Buyer(1, "ada", "gold")
            persist(ada)
            persist(
                Purchase(1, "P-1", 150, ada),
                Purchase(2, "P-2", 50, ada),
                Purchase(3, "P-3", 400, null),
            )
        }

        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { it.seed() }
                block(jpa)
            }

        feature("reading").config(enabled = JpaTestDatabase.available) {
            scenario("finds all, and all that match a specification") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.findAll(session).map { it.reference } shouldContainExactlyInAnyOrder
                            listOf("P-1", "P-2", "P-3")
                        purchases.findAll(session) { Purchase::total gt 100L }.map { it.reference } shouldContainExactlyInAnyOrder
                            listOf("P-1", "P-3")
                    }
                }
            }

            scenario("finds by id, requires by id, and names the entity when there is none") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.findById(session, 1L)?.reference shouldBe "P-1"
                        purchases.findById(session, 99L) shouldBe null
                        purchases.requireById(session, 1L).reference shouldBe "P-1"
                    }

                    shouldThrow<JpaNotFoundException> {
                        jpa.session { session -> purchases.requireById(session, 99L) }
                    }.message.shouldNotBeNull() shouldContain "Purchase"
                }
            }

            scenario("finds by a set of ids, and asks for nothing when the set is empty") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.findByIds(session, listOf(1L, 3L, 99L)).map { it.reference } shouldContainExactlyInAnyOrder
                            listOf("P-1", "P-3")
                        purchases.findByIds(session, emptyList()) shouldContainExactly emptyList()
                    }
                }
            }

            scenario("counts, and answers whether anything matches") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.count(session) shouldBe 3L
                        purchases.count(session) { Purchase::total gt 100L } shouldBe 2L
                        purchases.exists(session) { Purchase::reference eq "P-2" } shouldBe true
                        purchases.exists(session) { Purchase::reference eq "P-9" } shouldBe false
                        purchases.existsById(session, 1L) shouldBe true
                        purchases.existsById(session, 99L) shouldBe false
                    }
                }
            }

            scenario("answers which ids exist, reading one column rather than the entities") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.existingIds(session, listOf(1L, 99L, 3L)) shouldContainExactlyInAnyOrder listOf(1L, 3L)
                        purchases.existingIds(session, emptyList()) shouldContainExactly emptyList()
                    }
                }
            }

            scenario("finds one by specification, and pages by keyset") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.findOne(session) { Purchase::reference eq "P-2" }?.total shouldBe 50L

                        val page = purchases.findPage(session, PageRequest.first(2)) { sortBy(Purchase::id) }
                        page.data.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                        page.info.hasNextPage shouldBe true

                        val next = purchases.findPage(session, PageRequest.first(2, page.info.endCursor)) { sortBy(Purchase::id) }
                        next.data.map { it.reference } shouldContainExactly listOf("P-3")
                    }
                }
            }
        }

        feature("writing").config(enabled = JpaTestDatabase.available) {
            scenario("inserts one and many, which reach the database when the transaction ends") {
                seeded { jpa ->
                    jpa.transaction { session -> purchases.insert(session, Purchase(4, "P-4", 10)) }
                    jpa.transaction { session ->
                        purchases.insertAll(session, listOf(Purchase(5, "P-5", 10), Purchase(6, "P-6", 10)))
                    }

                    jpa.session { session -> purchases.count(session) } shouldBe 6L
                }
            }

            scenario("updates through a merge, so a detached instance is copied onto the managed one") {
                seeded { jpa ->
                    jpa.transaction { session -> purchases.update(session, Purchase(1, "P-1", 999)) }

                    jpa.session { session -> purchases.requireById(session, 1L).total } shouldBe 999L
                }
            }

            scenario("deletes by id, and says whether there was anything there") {
                seeded { jpa ->
                    jpa.transaction { session -> purchases.deleteById(session, 2L) } shouldBe true
                    jpa.transaction { session -> purchases.deleteById(session, 99L) } shouldBe false

                    jpa.session { session -> purchases.findAll(session).map { it.reference } } shouldContainExactlyInAnyOrder
                        listOf("P-1", "P-3")
                }
            }

            scenario("deletes by a set of ids, counting only the ones that were there") {
                seeded { jpa ->
                    jpa.transaction { session -> purchases.deleteByIds(session, listOf(1L, 3L, 99L)) } shouldBe 2

                    jpa.session { session -> purchases.count(session) } shouldBe 1L
                }
            }

            scenario("a delete through the repository is seen by the session that did it") {
                seeded { jpa ->
                    jpa.transaction { session ->
                        purchases.deleteById(session, 1L)
                        session.flush()

                        // A bulk `delete` would have left this session holding the removed row; the
                        // repository loads and removes, so the persistence context agrees with the
                        // database before the transaction ends.
                        purchases.findById(session, 1L) shouldBe null
                    }
                }
            }
        }

        feature("a subclass").config(enabled = JpaTestDatabase.available) {
            scenario("adds the queries that are specific to its entity") {
                class PurchaseRepository : JpaRepository<Purchase, Long>(Purchase::class, Purchase::id) {
                    suspend fun findByBuyer(
                        session: JpaSession,
                        buyer: String,
                    ): List<Purchase> = findAll(session) { join(Purchase::customer)[Buyer::name] eq buyer }
                }

                seeded { jpa ->
                    jpa.session { session ->
                        PurchaseRepository().findByBuyer(session, "ada").map { it.reference } shouldContainExactlyInAnyOrder
                            listOf("P-1", "P-2")
                    }
                }
            }
        }
    })
