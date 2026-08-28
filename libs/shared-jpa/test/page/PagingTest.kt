package com.strange.jpa.page

import com.strange.jpa.Jpa
import com.strange.jpa.JpaPaginationException
import com.strange.jpa.JpaTestDatabase
import com.strange.jpa.dsl.asc
import com.strange.jpa.dsl.select
import com.strange.jpa.entity.Buyer
import com.strange.jpa.entity.Purchase
import com.strange.jpa.entity.PurchaseLine
import com.strange.jpa.entity.Thing
import com.strange.jpa.session.session
import com.strange.jpa.session.transaction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Keyset pagination: that it walks every row once, and refuses the sorts that would not. */
class PagingTest :
    FeatureSpec({

        // Six purchases, three of them sharing a total — the case an offset pager gets wrong.
        suspend fun <T> seeded(block: suspend (Jpa) -> T): T =
            JpaTestDatabase.withJpa(Buyer::class, Purchase::class, PurchaseLine::class) { jpa ->
                jpa.transaction { session ->
                    session.persist(
                        Purchase(1, "P-1", 100),
                        Purchase(2, "P-2", 100),
                        Purchase(3, "P-3", 100),
                        Purchase(4, "P-4", 200),
                        Purchase(5, "P-5", 300),
                        Purchase(6, "P-6", 400),
                    )
                }
                block(jpa)
            }

        suspend fun Jpa.page(request: PageRequest) =
            session { session ->
                session
                    .select<Purchase>()
                    .sortBy(Purchase::total)
                    .sortBy(Purchase::id)
                    .page(request)
            }

        feature("a forward walk").config(enabled = JpaTestDatabase.available) {
            scenario("returns every row exactly once, over a sort key that repeats") {
                seeded { jpa ->
                    val seen = mutableListOf<String>()
                    var cursor: String? = null

                    do {
                        val page = jpa.page(PageRequest.first(2, cursor))
                        seen += page.data.map { it.reference }
                        cursor = page.info.endCursor
                    } while (page.info.hasNextPage)

                    seen shouldContainExactly listOf("P-1", "P-2", "P-3", "P-4", "P-5", "P-6")
                }
            }

            scenario("says there is another page without asking a second query") {
                seeded { jpa ->
                    val first = jpa.page(PageRequest.first(2))

                    first.data.map { it.reference } shouldContainExactly listOf("P-1", "P-2")
                    first.info.hasNextPage shouldBe true
                    first.info.hasPreviousPage shouldBe false

                    val last = jpa.page(PageRequest.first(2, jpa.page(PageRequest.first(4)).info.endCursor))
                    last.data.map { it.reference } shouldContainExactly listOf("P-5", "P-6")
                    last.info.hasNextPage shouldBe false
                    last.info.hasPreviousPage shouldBe true
                }
            }

            scenario("one query answers for every page, rather than being spent on the first") {
                seeded { jpa ->
                    jpa.session { session ->
                        // The same scope object, paged twice. If the keyset predicate were added to
                        // the query rather than handed to the build, the second page would ask for
                        // the rows after P-2 *and* after P-4 at once — two pages in and every page
                        // after it empty.
                        val query = session.select<Purchase>().sortBy(Purchase::total).sortBy(Purchase::id)

                        val first = query.page(PageRequest.first(2))
                        first.data.map { it.reference } shouldContainExactly listOf("P-1", "P-2")

                        val second = query.page(PageRequest.first(2, first.info.endCursor))
                        second.data.map { it.reference } shouldContainExactly listOf("P-3", "P-4")

                        query.page(PageRequest.first(2)).data.map { it.reference } shouldContainExactly
                            listOf("P-1", "P-2")
                    }
                }
            }

            scenario("skips nothing and repeats nothing when a row is inserted between two pages") {
                seeded { jpa ->
                    val first = jpa.page(PageRequest.first(2))
                    first.data.map { it.reference } shouldContainExactly listOf("P-1", "P-2")

                    // an offset pager would now serve P-3 twice, or never
                    jpa.transaction { it.persist(Purchase(7, "P-0", 100)) }

                    val second = jpa.page(PageRequest.first(2, first.info.endCursor))

                    second.data.map { it.reference } shouldContainExactly listOf("P-3", "P-0")
                }
            }
        }

        feature("a backward walk").config(enabled = JpaTestDatabase.available) {
            scenario("returns the rows in the same order as forward, from the other end") {
                seeded { jpa ->
                    val end = jpa.page(PageRequest.last(2))

                    end.data.map { it.reference } shouldContainExactly listOf("P-5", "P-6")
                    end.info.hasNextPage shouldBe false
                    end.info.hasPreviousPage shouldBe true

                    val earlier = jpa.page(PageRequest.last(2, end.info.startCursor))
                    earlier.data.map { it.reference } shouldContainExactly listOf("P-3", "P-4")
                }
            }

            scenario("walks the whole result set backwards, once each") {
                seeded { jpa ->
                    val seen = mutableListOf<String>()
                    var cursor: String? = null

                    do {
                        val page = jpa.page(PageRequest.last(2, cursor))
                        seen.addAll(0, page.data.map { it.reference })
                        cursor = page.info.startCursor
                    } while (page.info.hasPreviousPage)

                    seen shouldContainExactly listOf("P-1", "P-2", "P-3", "P-4", "P-5", "P-6")
                }
            }
        }

        feature("a descending sort").config(enabled = JpaTestDatabase.available) {
            scenario("pages the other way round, still once each") {
                seeded { jpa ->
                    val seen = mutableListOf<String>()
                    var cursor: String? = null

                    do {
                        val page =
                            jpa.session { session ->
                                session
                                    .select<Purchase>()
                                    .sortBy(Purchase::total, descending = true)
                                    .sortBy(Purchase::id)
                                    .page(PageRequest.first(2, cursor))
                            }
                        seen += page.data.map { it.reference }
                        cursor = page.info.endCursor
                    } while (page.info.hasNextPage)

                    seen shouldContainExactly listOf("P-6", "P-5", "P-4", "P-1", "P-2", "P-3")
                }
            }
        }

        feature("what it refuses").config(enabled = JpaTestDatabase.available) {
            scenario("a sort that does not end in the identifier, naming what is missing") {
                seeded { jpa ->
                    val refused =
                        shouldThrow<JpaPaginationException> {
                            jpa.session { session ->
                                session.select<Purchase>().sortBy(Purchase::total).page(PageRequest.first(2))
                            }
                        }

                    refused.message.shouldNotBeNull() shouldContain "sortBy(Purchase::id)"
                }
            }

            scenario("a paged query with no sort at all") {
                seeded { jpa ->
                    shouldThrow<JpaPaginationException> {
                        jpa.session { session -> session.select<Purchase>().page(PageRequest.first(2)) }
                    }.message.shouldNotBeNull() shouldContain "needs a sort"
                }
            }

            scenario("an orderBy, which a cursor cannot be read back out of") {
                seeded { jpa ->
                    shouldThrow<JpaPaginationException> {
                        jpa.session { session ->
                            session
                                .select<Purchase>()
                                .orderBy { asc(Purchase::id) }
                                .sortBy(Purchase::id)
                                .page(PageRequest.first(2))
                        }
                    }.message.shouldNotBeNull() shouldContain "sortBy, not orderBy"
                }
            }

            scenario("a cursor issued for a different sort order") {
                seeded { jpa ->
                    val cursor = jpa.page(PageRequest.first(2)).info.endCursor

                    shouldThrow<JpaPaginationException> {
                        jpa.session { session ->
                            session
                                .select<Purchase>()
                                .sortBy(Purchase::reference)
                                .sortBy(Purchase::id)
                                .page(PageRequest.first(2, cursor))
                        }
                    }.message.shouldNotBeNull() shouldContain "different sort order"
                }
            }

            scenario("a cursor that is not one this library issued") {
                seeded { jpa ->
                    shouldThrow<JpaPaginationException> {
                        jpa.page(PageRequest.first(2, "not-a-cursor"))
                    }.message.shouldNotBeNull() shouldContain "not one this query issued"
                }
            }

            scenario("both directions at once, and a page of nothing") {
                shouldThrow<JpaPaginationException> { PageRequest(first = 1, last = 1) }
                shouldThrow<JpaPaginationException> { PageRequest.first(0) }
            }
        }

        feature("the restrictions and joins every other query has").config(enabled = JpaTestDatabase.available) {
            scenario("apply to a page too") {
                seeded { jpa ->
                    val page =
                        jpa.session { session ->
                            session
                                .select<Purchase>()
                                .where { Purchase::total gt 100L }
                                .sortBy(Purchase::id)
                                .page(PageRequest.first(10))
                        }

                    page.data.map { it.reference } shouldContainExactly listOf("P-4", "P-5", "P-6")
                    page.info.hasNextPage shouldBe false
                }
            }

            scenario("a page of a string key sorts and resumes on it") {
                JpaTestDatabase.withJpa(Thing::class) { jpa ->
                    jpa.transaction { session ->
                        session.persist(Thing(1, "delta"), Thing(2, "alpha"), Thing(3, "charlie"))
                    }

                    val first =
                        jpa.session { session ->
                            session
                                .select<Thing>()
                                .sortBy(Thing::name)
                                .sortBy(Thing::id)
                                .page(PageRequest.first(2))
                        }
                    first.data.map { it.name } shouldContainExactly listOf("alpha", "charlie")

                    val second =
                        jpa.session { session ->
                            session
                                .select<Thing>()
                                .sortBy(Thing::name)
                                .sortBy(Thing::id)
                                .page(PageRequest.first(2, first.info.endCursor))
                        }
                    second.data.map { it.name } shouldContainExactly listOf("delta")
                }
            }

            scenario("the same rows the plain DSL returns, in the same order") {
                seeded { jpa ->
                    val paged = jpa.page(PageRequest(first = null)).data.map { it.reference }
                    val plain =
                        jpa
                            .session { session ->
                                session
                                    .select<Purchase> {
                                        orderBy {
                                            asc(Purchase::total)
                                        }
                                        orderBy {
                                            asc(Purchase::id)
                                        }
                                    }.list()
                            }.map { it.reference }

                    paged shouldContainExactly plain
                }
            }
        }
    })
