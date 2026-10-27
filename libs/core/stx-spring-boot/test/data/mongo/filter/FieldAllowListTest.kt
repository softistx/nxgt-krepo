package com.softistx.spring.data.mongo.filter

import com.softistx.spring.error.ApiException
import com.softistx.spring.web.parseSort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.http.HttpStatus
import org.springframework.mock.web.reactive.function.server.MockServerRequest

private val ALLOWED = setOf("status", "total", "address.city")

private fun request(
    filter: String? = null,
    sort: String? = null,
) = MockServerRequest
    .builder()
    .apply {
        filter?.let { queryParam("filter", it) }
        sort?.let { queryParam("sort", it) }
    }.build()

class FieldAllowListTest :
    FeatureSpec({
        feature("a filter field outside the allow-list") {
            scenario("is refused as filters.invalid, naming the clause") {
                val failure = shouldThrow<ApiException> { "passwordHash:like:a".parseFilter(ALLOWED) }

                failure.status shouldBe HttpStatus.BAD_REQUEST
                failure.key shouldBe KEY_INVALID_FILTER
                failure.args shouldBe mapOf("filter" to "passwordHash:like:a")
            }

            scenario("is refused in any clause of an or@ filter") {
                shouldThrow<ApiException> { "or@status:eq:PAID;passwordHash:eq:x".parseFilter(ALLOWED) }
            }

            scenario("is refused when only a parent of a dotted path is listed") {
                shouldThrow<ApiException> { "address.zip:eq:1".parseFilter(ALLOWED) }
                shouldThrow<ApiException> { "address:eq:1".parseFilter(ALLOWED) }
            }
        }

        feature("a sort field outside the allow-list") {
            scenario("is refused as filters.invalid") {
                val failure = shouldThrow<ApiException> { "passwordHash:DESC".parseSort().toSort(ALLOWED) }

                failure.status shouldBe HttpStatus.BAD_REQUEST
                failure.key shouldBe KEY_INVALID_FILTER
                failure.args shouldBe mapOf("filter" to "passwordHash:DESC")
            }
        }

        feature("a field inside the allow-list") {
            scenario("passes in a filter, dotted paths included") {
                "status:eq:PAID;address.city:eq:Paris".parseFilter(ALLOWED).queryObject.toString() shouldContain "Paris"
            }

            scenario("passes in a sort") {
                "total:DESC,status:ASC".parseSort().toSort(ALLOWED).toString() shouldBe "total: DESC,status: ASC"
            }
        }

        feature("no allow-list") {
            scenario("keeps every field reachable, as before") {
                "passwordHash:like:a".parseFilter().queryObject.toString() shouldContain "passwordHash"
                "passwordHash:DESC".parseSort().toSort().toString() shouldBe "passwordHash: DESC"
            }
        }

        feature("mongoPage") {
            scenario("refuses a filter field outside the allow-list") {
                shouldThrow<ApiException> { request(filter = "passwordHash:like:a").mongoPage(allowed = ALLOWED) }
            }

            scenario("refuses a sort field outside the allow-list") {
                shouldThrow<ApiException> { request(sort = "passwordHash:ASC").mongoPage(allowed = ALLOWED) }
            }

            scenario("passes allowed fields, and without a list passes any") {
                val page = request(filter = "status:eq:PAID", sort = "total:DESC").mongoPage(allowed = ALLOWED)
                page.sort.toString() shouldBe "total: DESC"
                request(filter = "secret:eq:1").mongoPage().query.queryObject.toString() shouldContain "secret"
            }
        }
    })
