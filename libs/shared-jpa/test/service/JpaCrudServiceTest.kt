package com.strange.jpa.service

import com.strange.jpa.Jpa
import com.strange.jpa.JpaNotFoundException
import com.strange.jpa.JpaOutsideTransactionException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** The flow a service repeats, and the two things this one refuses to do quietly. */
class JpaCrudServiceTest :
    FeatureSpec({

        suspend fun <T> withJpa(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class, block = block)

        feature("creating").config(enabled = JpaTestDatabase.available) {
            scenario("builds the entity, writes it, and runs the hooks around it") {
                withJpa { jpa ->
                    val service = PurchaseService()

                    val created = jpa.transaction { session -> service.create(session, NewPurchase(1, "P-1", 150)) }

                    created.reference shouldBe "P-1"
                    service.ran shouldContainExactly listOf("beforeCreate", "afterCreate")
                    jpa.session { session -> service.findById(session, 1L).total } shouldBe 150L
                }
            }

            scenario("flushes, so a constraint violation surfaces at the create and not at the commit") {
                withJpa { jpa ->
                    val service = PurchaseService()
                    jpa.transaction { session -> service.create(session, NewPurchase(1, "P-1")) }

                    shouldThrow<Throwable> {
                        jpa.transaction { session ->
                            service.create(session, NewPurchase(1, "duplicate"))

                            // Reached only if the flush did not happen — the duplicate key would
                            // then wait for the commit, and this line would run first.
                            service.ran += "kept going"
                        }
                    }

                    service.ran.contains("kept going") shouldBe false
                }
            }
        }

        feature("updating").config(enabled = JpaTestDatabase.available) {
            scenario("mutates the managed entity, and the dirty check writes what changed") {
                withJpa { jpa ->
                    val service = PurchaseService()
                    jpa.transaction { session -> service.create(session, NewPurchase(1, "P-1", 150)) }

                    val updated = jpa.transaction { session -> service.update(session, 1L, EditPurchase(total = 400)) }

                    updated.total shouldBe 400L
                    updated.reference shouldBe "P-1"
                    service.ran shouldContainExactly listOf("beforeCreate", "afterCreate", "beforeUpdate", "afterUpdate")
                    jpa.session { session -> service.findById(session, 1L).total } shouldBe 400L
                }
            }

            scenario("stamps who acted, when the service was told") {
                withJpa { jpa ->
                    val service = PurchaseService(principal = "ada")
                    jpa.transaction { session -> service.create(session, NewPurchase(1, "P-1")) }
                    jpa.transaction { session -> service.update(session, 1L, EditPurchase(total = 5)) }

                    jpa.session { session -> service.findById(session, 1L).reference } shouldBe "P-1/ada"
                }
            }

            scenario("refuses an id that is not there") {
                withJpa { jpa ->
                    shouldThrow<JpaNotFoundException> {
                        jpa.transaction { session -> PurchaseService().update(session, 99L, EditPurchase(total = 1)) }
                    }
                }
            }
        }

        feature("deleting").config(enabled = JpaTestDatabase.available) {
            scenario("removes one, and refuses an id that is not there") {
                withJpa { jpa ->
                    val service = PurchaseService()
                    jpa.transaction { session -> service.create(session, NewPurchase(1, "P-1")) }

                    jpa.transaction { session -> service.delete(session, 1L) }
                    jpa.session { session -> service.findByIdOrNull(session, 1L) } shouldBe null

                    shouldThrow<JpaNotFoundException> {
                        jpa.transaction { session -> service.delete(session, 1L) }
                    }
                }
            }

            scenario("deletes many, counting only the ones that were there") {
                withJpa { jpa ->
                    val service = PurchaseService()
                    jpa.transaction { session ->
                        service.create(session, NewPurchase(1, "P-1"))
                        service.create(session, NewPurchase(2, "P-2"))
                    }

                    jpa.transaction { session -> service.deleteAll(session, listOf(1L, 2L, 99L)) } shouldBe 2
                    jpa.session { session -> service.findAll(session) } shouldContainExactly emptyList()
                }
            }
        }

        feature("a write with no transaction").config(enabled = JpaTestDatabase.available) {
            scenario("is refused, rather than discarded without a word") {
                withJpa { jpa ->
                    val service = PurchaseService()

                    // session { } flushes nothing, so every one of these would have returned
                    // successfully and written no row.
                    val refused =
                        shouldThrow<JpaOutsideTransactionException> {
                            jpa.session { session -> service.create(session, NewPurchase(1, "P-1")) }
                        }

                    refused.message shouldContain "create on Purchase needs a transaction"
                    refused.message shouldContain "transaction { }"

                    shouldThrow<JpaOutsideTransactionException> {
                        jpa.session { session -> service.update(session, 1L, EditPurchase(total = 1)) }
                    }
                    shouldThrow<JpaOutsideTransactionException> {
                        jpa.session { session -> service.delete(session, 1L) }
                    }
                    shouldThrow<JpaOutsideTransactionException> {
                        jpa.session { session -> service.deleteAll(session, listOf(1L)) }
                    }

                    jpa.session { session -> service.findAll(session) } shouldContainExactly emptyList()
                }
            }

            scenario("does not stop a read, which needs no transaction at all") {
                withJpa { jpa ->
                    val service = PurchaseService()
                    jpa.transaction { session -> service.create(session, NewPurchase(1, "P-1")) }

                    jpa.session { session -> service.findAll(session).map { it.reference } } shouldContainExactly listOf("P-1")
                }
            }
        }
    })
