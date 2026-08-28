package com.strange.jpa.query

import com.strange.jpa.Jpa
import com.strange.jpa.JpaOutsideTransactionException
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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The write verbs, and the guard that is the reason they exist as more than aliases.
 *
 * `persist`, `merge` and `remove` on the session are JPA's own primitives and promise only that the
 * instance is managed. These promise the row was written, so they refuse a session that cannot
 * write — which is the check that used to live in `JpaCrudService`, a layer above the one every
 * caller actually used.
 */
class WritesTest :
    FeatureSpec({

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

        feature("writing").config(enabled = JpaTestDatabase.available) {
            scenario("inserts one and many, which reach the database when the transaction ends") {
                seeded { jpa ->
                    jpa.transaction { session -> session.insert(Purchase(4, "P-4", 10)) }
                    jpa.transaction { session ->
                        session.insertAll(listOf(Purchase(5, "P-5", 10), Purchase(6, "P-6", 10)))
                    }

                    jpa.session { session -> session.count<Purchase>() } shouldBe 6L
                }
            }

            scenario("updates through a merge, so a detached instance is copied onto the managed one") {
                seeded { jpa ->
                    jpa.transaction { session -> session.update(Purchase(1, "P-1", 999)) }

                    jpa.session { session -> session.get<Purchase>(1L).total } shouldBe 999L
                }
            }

            scenario("deletes by id, and says whether there was anything there") {
                seeded { jpa ->
                    jpa.transaction { session -> session.deleteById<Purchase>(2L) } shouldBe true
                    jpa.transaction { session -> session.deleteById<Purchase>(99L) } shouldBe false

                    jpa.session { session -> session.findAll<Purchase>().map { it.reference } } shouldContainExactlyInAnyOrder
                        listOf("P-1", "P-3")
                }
            }

            scenario("deletes by a set of ids, counting only the ones that were there") {
                seeded { jpa ->
                    jpa.transaction { session -> session.deleteByIds(Purchase::id, listOf(1L, 3L, 99L)) } shouldBe 2

                    jpa.session { session -> session.count<Purchase>() } shouldBe 1L
                }
            }

            scenario("a delete is seen by the session that did it") {
                seeded { jpa ->
                    jpa.transaction { session ->
                        session.deleteById<Purchase>(1L)
                        session.flush()

                        // A bulk `delete` would have left this session holding the removed row; these
                        // load and remove, so the persistence context agrees with the database before
                        // the transaction ends.
                        session.find<Purchase>(1L) shouldBe null
                    }
                }
            }
        }

        feature("a write with no transaction").config(enabled = JpaTestDatabase.available) {
            scenario("is refused rather than discarded, by every write verb") {
                seeded { jpa ->
                    jpa.session { session ->
                        val refused =
                            listOf<suspend () -> Any?>(
                                { session.insert(Purchase(9, "P-9", 1, null)) },
                                { session.insertAll(listOf(Purchase(10, "P-10", 1, null))) },
                                { session.update(Purchase(1, "P-1", 999, null)) },
                                { session.delete(session.get<Purchase>(1L)) },
                                { session.deleteById<Purchase>(1L) },
                                { session.deleteByIds(Purchase::id, listOf(1L, 2L)) },
                            )
                        refused.forEach { write ->
                            shouldThrow<JpaOutsideTransactionException> { write() }
                                .message shouldContain "needs a transaction"
                        }
                    }

                    // And nothing was written: the point of refusing is that the alternative is a
                    // call that reports success and left no row.
                    jpa.session { it.count<Purchase>() } shouldBe 3L
                }
            }

            scenario("names the operation and the entity, so the message says which call it was") {
                seeded { jpa ->
                    jpa.session { session ->
                        shouldThrow<JpaOutsideTransactionException> { session.deleteById<Purchase>(1L) }
                            .let {
                                it.operation shouldBe "deleteById"
                                it.type shouldBe Purchase::class
                            }
                    }
                }
            }

            // The primitives stay honest rather than guarded: `persist` promises only that the
            // instance is managed, which is true, and a caller reaching for it has asked for JPA's
            // own contract rather than for a row.
            scenario("does not reach persist, which promises only that the instance is managed") {
                seeded { jpa ->
                    jpa.session { session -> session.persist(Purchase(11, "P-11", 1, null)) }

                    jpa.session { it.count<Purchase>() } shouldBe 3L
                }
            }

            scenario("does not touch the reads, which are an ordinary thing to want outside one") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.count<Purchase>() shouldBe 3L
                        session.find<Purchase>(1L).shouldNotBeNull()
                    }
                }
            }
        }

        feature("by identifier").config(enabled = JpaTestDatabase.available) {
            scenario("finds by a set of ids, and asks for nothing when the set is empty") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.findByIds(Purchase::id, listOf(1L, 3L, 99L)).map { it.reference } shouldContainExactlyInAnyOrder
                            listOf("P-1", "P-3")
                        session.findByIds(Purchase::id, emptyList()) shouldContainExactly emptyList()
                    }
                }
            }

            scenario("answers whether one id is there") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.existsById(Purchase::id, 1L) shouldBe true
                        session.existsById(Purchase::id, 99L) shouldBe false
                    }
                }
            }

            // The projection targets `ID::class.javaObjectType`: `Long::class.java` is `long.class`,
            // which a criteria selecting a column cannot project into.
            scenario("answers which ids exist, reading one column rather than the entities") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.existingIds(Purchase::id, listOf(1L, 99L, 3L)) shouldContainExactlyInAnyOrder listOf(1L, 3L)
                        session.existingIds(Purchase::id, emptyList()) shouldContainExactly emptyList()
                    }
                }
            }
        }
    })
