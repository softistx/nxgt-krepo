package com.strange.jpa.repository

import com.strange.jpa.Jpa
import com.strange.jpa.JpaMappingException
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.JpaOutsideTransactionException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.criteria.asc
import com.strange.jpa.criteria.eq
import com.strange.jpa.criteria.fetch
import com.strange.jpa.criteria.get
import com.strange.jpa.criteria.gt
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Keyed
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.entity.Ticket
import com.strange.jpa.session.JpaSession
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root

/** One entity as an object: the criteria it writes for you, and a unit of work taken per call. */
class JpaRepositoryTest :
    FeatureSpec({

        val purchases = JpaRepository(Purchase::id)

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
                        purchases.findAll(session) { it[Purchase::total] gt 100L }.map { it.reference } shouldContainExactlyInAnyOrder
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
                        purchases.count(session) { it[Purchase::total] gt 100L } shouldBe 2L
                        purchases.exists(session) { it[Purchase::reference] eq "P-2" } shouldBe true
                        purchases.exists(session) { it[Purchase::reference] eq "P-9" } shouldBe false
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

            // A repository answers with entities, so the question of what a query loads reaches it
            // too — and a JpaSpec cannot fetch, because the same spec has to fit a projection.
            scenario("hands back the query itself, which is where a fetch goes") {
                seeded { jpa ->
                    shouldThrowAny {
                        jpa.session { session -> purchases.findAll(session).map { it.customer?.name } }
                    }

                    jpa
                        .session { session ->
                            purchases
                                .query(session, { it[Purchase::total] gt 0L }) { criteria, purchase ->
                                    purchase.fetch(Purchase::customer)
                                    criteria.orderBy(asc(purchase[Purchase::id]))
                                }.list()
                        }.map { it.customer?.name } shouldContainExactly listOf("ada", "ada", null)
                }
            }

            scenario("finds one by specification, and pages by limit and offset") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.findOne(session) { it[Purchase::reference] eq "P-2" }?.total shouldBe 50L

                        // One row beyond the page is asked for and discarded; whether it turned up
                        // is the whole of `hasNextPage`, with no second count query.
                        val ordered: (CriteriaQuery<Purchase>, Root<Purchase>) -> Unit =
                            { criteria, purchase -> criteria.orderBy(asc(purchase[Purchase::id])) }

                        val page = purchases.findPage(session, limit = 2, shape = ordered)
                        page.data.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                        page.info.hasNextPage shouldBe true
                        page.info.hasPreviousPage shouldBe false

                        val next = purchases.findPage(session, limit = 2, offset = 2, shape = ordered)
                        next.data.map { it.reference } shouldContainExactly listOf("P-3")
                        next.info.hasNextPage shouldBe false
                        next.info.hasPreviousPage shouldBe true
                    }
                }
            }

            scenario("refuses a page size below one, and a negative offset") {
                seeded { jpa ->
                    jpa.session { session ->
                        shouldThrow<IllegalArgumentException> { purchases.findPage(session, limit = 0) }
                        shouldThrow<IllegalArgumentException> { purchases.findPage(session, limit = 1, offset = -1) }
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

        // The guard used to live only in JpaCrudService, one layer up, while this class is public,
        // open, and what a custom subclass writes against.
        feature("a write with no transaction").config(enabled = JpaTestDatabase.available) {
            scenario("is refused rather than discarded, by every write on the repository") {
                seeded { jpa ->
                    jpa.session { session ->
                        val refused =
                            listOf<suspend () -> Any?>(
                                { purchases.insert(session, Purchase(9, "P-9", 1, null)) },
                                { purchases.insertAll(session, listOf(Purchase(10, "P-10", 1, null))) },
                                { purchases.update(session, Purchase(1, "P-1", 999, null)) },
                                { purchases.delete(session, purchases.requireById(session, 1L)) },
                                { purchases.deleteById(session, 1L) },
                                { purchases.deleteByIds(session, listOf(1L, 2L)) },
                            )
                        refused.forEach { write ->
                            shouldThrow<JpaOutsideTransactionException> { write() }
                                .message shouldContain "needs a transaction"
                        }
                    }

                    // And nothing was written: the point of refusing is that the alternative is a
                    // call that reports success and left no row.
                    jpa.session { purchases.count(it) } shouldBe 3L
                }
            }

            scenario("names the operation and the entity, so the message says which call it was") {
                seeded { jpa ->
                    jpa.session { session ->
                        shouldThrow<JpaOutsideTransactionException> { purchases.deleteById(session, 1L) }
                            .let {
                                it.operation shouldBe "deleteById"
                                it.type shouldBe Purchase::class
                            }
                    }
                }
            }

            scenario("does not touch the reads, which are an ordinary thing to want outside one") {
                seeded { jpa ->
                    jpa.session { session ->
                        purchases.count(session) shouldBe 3L
                        purchases.findById(session, 1L).shouldNotBeNull()
                    }
                }
            }
        }

        feature("the entity it is over").config(enabled = JpaTestDatabase.available) {
            scenario("comes off the property reference, with nothing naming the class") {
                purchases.entity shouldBe Purchase::class
                purchases.name shouldBe "Purchase"
            }

            scenario("is the entity referred to, not the class that declared the identifier") {
                // `Keyed` is a @MappedSuperclass and declares `id`; `Ticket` refers to it. Resolving
                // to `Keyed` would not fail — it would query the wrong thing, or nothing mapped.
                val tickets = JpaRepository(Ticket::id)
                tickets.entity shouldBe Ticket::class

                JpaTestDatabase.withJpa(Ticket::class) { jpa ->
                    jpa.transaction { session -> tickets.insert(session, Ticket(1, "a leak")) }

                    jpa.session { session ->
                        tickets.requireById(session, 1L).subject shouldBe "a leak"
                        tickets.findAll(session).size shouldBe 1
                    }
                }
            }

            scenario("is refused when the reference names a class that is not an entity") {
                // The mirror of the scenario above, and the case it used to get wrong: `Keyed::id`
                // type-checks as JpaRepository<Keyed, Long> and used to build happily, failing only
                // on the first query with a Hibernate UnknownEntityTypeException out of a
                // CompletionStage full of Vert.x frames.
                shouldThrow<JpaMappingException> { JpaRepository(Keyed::id) }
                    .message
                    .shouldNotBeNull() shouldContain "Keyed is not an @Entity"
            }

            scenario("is refused when the reference is not a property of anything") {
                val notAProperty: (Purchase) -> Long = { it.id }
                shouldThrow<JpaMappingException> {
                    JpaRepository(
                        object : kotlin.reflect.KProperty1<Purchase, Long> by Purchase::id {
                            override fun get(receiver: Purchase): Long = notAProperty(receiver)
                        },
                    )
                }.message.shouldNotBeNull() shouldContain "property reference"
            }
        }

        feature("a subclass").config(enabled = JpaTestDatabase.available) {
            scenario("adds the queries that are specific to its entity") {
                class PurchaseRepository : JpaRepository<Purchase, Long>(Purchase::id) {
                    suspend fun findByBuyer(
                        session: JpaSession,
                        buyer: String,
                    ): List<Purchase> = findAll(session) { it[Purchase::customer][Buyer::name] eq buyer }
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
