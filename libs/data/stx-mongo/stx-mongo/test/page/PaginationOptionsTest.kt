package com.softistx.mongo.page

import com.softistx.mongo.InvalidPaginationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

/**
 * Contradictory pagination is rejected where it is built, not where it is used — by the time a
 * malformed request reaches the server the caller has lost the context to say what was wrong with
 * it.
 */
class PaginationOptionsTest :
    FeatureSpec({

        feature("what a caller asked for") {
            scenario("first pages forward, last pages backward") {
                PaginationOptions.first(10).let {
                    it.limit shouldBe 10
                    it.forward shouldBe true
                }
                PaginationOptions.last(10).let {
                    it.limit shouldBe 10
                    it.forward shouldBe false
                }
            }

            scenario("neither means the whole result set") {
                PaginationOptions().limit shouldBe null
            }
        }

        feature("what a caller cannot ask for") {
            scenario("both directions at once") {
                shouldThrow<InvalidPaginationException> { PaginationOptions(first = 5, last = 5) }
            }

            scenario("a page of nothing") {
                shouldThrow<InvalidPaginationException> { PaginationOptions.first(0) }
                shouldThrow<InvalidPaginationException> { PaginationOptions.last(-1) }
            }
        }
    })
