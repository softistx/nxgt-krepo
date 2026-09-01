package com.softistx.jpa.query

import com.softistx.jpa.Jpa
import com.softistx.jpa.JpaNotFoundException
import com.softistx.jpa.JpaTestDatabase
import com.softistx.jpa.criteria.asc
import com.softistx.jpa.criteria.eq
import com.softistx.jpa.criteria.fetch
import com.softistx.jpa.criteria.get
import com.softistx.jpa.criteria.gt
import com.softistx.jpa.entity.Buyer
import com.softistx.jpa.entity.Purchase
import com.softistx.jpa.entity.PurchaseLine
import com.softistx.jpa.session.JpaSession
import com.softistx.jpa.session.session
import com.softistx.jpa.session.transaction
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

/**
 * The reads, as extensions on the session — the criteria every entity would otherwise repeat.
 *
 * `T` is reified, so nothing here works out at runtime which entity it is over and there is no
 * repository object to construct. `session.findAll<Purchase>()` is the whole of it.
 */
class SelectsTest :
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

        feature("reading").config(enabled = JpaTestDatabase.available) {
            scenario("finds all, and all that match a specification") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.findAll<Purchase>().map { it.reference } shouldContainExactlyInAnyOrder
                            listOf("P-1", "P-2", "P-3")
                        session.findAll<Purchase> { it[Purchase::total] gt 100L }.map { it.reference } shouldContainExactlyInAnyOrder
                            listOf("P-1", "P-3")
                    }
                }
            }

            // `find` and `get` are the session's own, and were already there — a single row by
            // identifier never needed a repository.
            scenario("finds by id, gets by id, and names the entity when there is none") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.find<Purchase>(1L)?.reference shouldBe "P-1"
                        session.find<Purchase>(99L) shouldBe null
                        session.get<Purchase>(1L).reference shouldBe "P-1"
                    }

                    shouldThrow<JpaNotFoundException> {
                        jpa.session { session -> session.get<Purchase>(99L) }
                    }.message.shouldNotBeNull() shouldContain "Purchase"
                }
            }

            scenario("counts, and answers whether anything matches") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.count<Purchase>() shouldBe 3L
                        session.count<Purchase> { it[Purchase::total] gt 100L } shouldBe 2L
                        session.exists<Purchase> { it[Purchase::reference] eq "P-2" } shouldBe true
                        session.exists<Purchase> { it[Purchase::reference] eq "P-9" } shouldBe false
                    }
                }
            }

            // The reads answer with entities, so what a query loads reaches them too — and a JpaSpec
            // cannot fetch, because the same spec has to fit a projection.
            scenario("select hands back the query itself, which is where a fetch goes") {
                seeded { jpa ->
                    shouldThrowAny {
                        jpa.session { session -> session.findAll<Purchase>().map { it.customer?.name } }
                    }

                    jpa
                        .session { session ->
                            session
                                .select<Purchase>({ it[Purchase::total] gt 0L }) { criteria, purchase ->
                                    purchase.fetch(Purchase::customer)
                                    criteria.orderBy(asc(purchase[Purchase::id]))
                                }.list()
                        }.map { it.customer?.name } shouldContainExactly listOf("ada", "ada", null)
                }
            }

            scenario("finds one by specification, and pages by limit and offset") {
                seeded { jpa ->
                    jpa.session { session ->
                        session.findOne<Purchase> { it[Purchase::reference] eq "P-2" }?.total shouldBe 50L

                        // One row beyond the page is asked for and discarded; whether it turned up
                        // is the whole of `hasNextPage`, with no second count query.
                        val ordered: (CriteriaQuery<Purchase>, Root<Purchase>) -> Unit =
                            { criteria, purchase -> criteria.orderBy(asc(purchase[Purchase::id])) }

                        val page = session.findPage(limit = 2, shape = ordered)
                        page.data.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                        page.info.hasNextPage shouldBe true
                        page.info.hasPreviousPage shouldBe false

                        val next = session.findPage(limit = 2, offset = 2, shape = ordered)
                        next.data.map { it.reference } shouldContainExactly listOf("P-3")
                        next.info.hasNextPage shouldBe false
                        next.info.hasPreviousPage shouldBe true
                    }
                }
            }

            scenario("refuses a page size below one, and a negative offset") {
                seeded { jpa ->
                    jpa.session { session ->
                        shouldThrow<IllegalArgumentException> { session.findPage<Purchase>(limit = 0) }
                        shouldThrow<IllegalArgumentException> { session.findPage<Purchase>(limit = 1, offset = -1) }
                    }
                }
            }
        }
    })
