package com.softistx.common.page

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private class Window(
    override val first: Int? = null,
    override val last: Int? = null,
    override val cursor: String? = null,
) : PageWindow {
    init {
        check(::IllegalArgumentException)
    }
}

/**
 * The half of keyset pagination that is the same in every store: what a window means, and how the
 * one extra row becomes a [PageInfo]. `stx-jpa` and `stx-mongo` both go through this, so a
 * disagreement between them would be a disagreement with these.
 */
class PagingTest :
    FeatureSpec({

        feature("a window") {
            scenario("derives its size and direction from whichever end was asked for") {
                Window(first = 10).let {
                    it.limit shouldBe 10
                    it.forward shouldBe true
                }
                Window(last = 10).let {
                    it.limit shouldBe 10
                    it.forward shouldBe false
                }
            }

            scenario("with neither end means the whole result set, forward") {
                Window().limit shouldBe null
                Window().forward shouldBe true
            }

            scenario("refuses a contradiction, through the store's own exception") {
                shouldThrow<IllegalArgumentException> { Window(first = 1, last = 1) }
                    .message shouldBe "first and last cannot both be set: pick a direction"
            }

            scenario("refuses a page smaller than one row") {
                shouldThrow<IllegalArgumentException> { Window(first = 0) }
                    .message shouldBe "a page size must be at least 1, got 0"
                shouldThrow<IllegalArgumentException> { Window(last = -3) }
                    .message shouldBe "a page size must be at least 1, got -3"
            }
        }

        feature("assembling a page") {
            // The rows a query for `limit + 1` returned, in the order the database read them.
            fun rows(vararg values: String) = values.toList()

            scenario("trims the row fetched to answer 'is there another page'") {
                val page = pageOf(rows("a", "b", "c"), limit = 2, forward = true, resumed = false, { it }, { it })
                page.data shouldContainExactly listOf("a", "b")
                page.info.hasNextPage shouldBe true
                page.info.endCursor shouldBe "b"
            }

            scenario("reports no next page when the extra row did not turn up") {
                val page = pageOf(rows("a", "b"), limit = 2, forward = true, resumed = false, { it }, { it })
                page.data shouldContainExactly listOf("a", "b")
                page.info.hasNextPage shouldBe false
            }

            scenario("a backward page is handed back the way the caller reads it") {
                val page = pageOf(rows("c", "b", "a"), limit = 2, forward = false, resumed = true, { it }, { it })
                page.data shouldContainExactly listOf("b", "c")
                page.info.startCursor shouldBe "b"
                page.info.endCursor shouldBe "c"
            }

            // Knowing for certain costs a query in the other direction; having paged in is proof enough.
            scenario("takes the far side of the page from whether the caller resumed") {
                pageOf(rows("a"), 5, forward = true, resumed = true, { it }, { it })
                    .info
                    .hasPreviousPage shouldBe true
                pageOf(rows("a"), 5, forward = false, resumed = true, { it }, { it })
                    .info
                    .hasNextPage shouldBe true
                pageOf(rows("a"), 5, forward = true, resumed = false, { it }, { it })
                    .info
                    .hasPreviousPage shouldBe false
            }

            scenario("with no limit keeps every row and knows there is no more") {
                val page = pageOf(rows("a", "b", "c"), limit = null, forward = true, resumed = false, { it }, { it })
                page.data shouldContainExactly listOf("a", "b", "c")
                page.info.hasNextPage shouldBe false
            }

            scenario("an empty result set has no cursors to offer") {
                val page = pageOf(rows(), limit = 2, forward = true, resumed = false, { it }, { it })
                page.data.shouldContainExactly(emptyList())
                page.info shouldBe PageInfo()
            }

            // Mongo decodes each document into the caller's type, and only the surviving ones.
            scenario("maps only the rows that survived the trim") {
                val decoded = mutableListOf<String>()
                val page =
                    pageOf(rows("a", "b", "c"), limit = 2, forward = true, resumed = false, { it }) {
                        decoded += it
                        it.uppercase()
                    }
                page.data shouldContainExactly listOf("A", "B")
                decoded shouldContainExactly listOf("a", "b")
            }
        }
    })
