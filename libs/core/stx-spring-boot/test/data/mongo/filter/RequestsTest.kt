package com.softistx.spring.data.mongo.filter

import com.softistx.spring.web.SortOrder
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.data.domain.Sort
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerRequest

private fun request(uri: String): ServerRequest =
    ServerRequest.create(
        MockServerWebExchange.from(MockServerHttpRequest.get(uri)),
        HandlerStrategies.withDefaults().messageReaders(),
    )

class RequestsTest :
    StringSpec({
        "the framework-free sort becomes Spring Data's" {
            listOf(SortOrder("name"), SortOrder("price", descending = true)).toSort() shouldBe
                Sort.by(Sort.Order.asc("name"), Sort.Order.desc("price"))
        }

        "no orders is unsorted, not an empty Sort" {
            emptyList<SortOrder>().toSort() shouldBe Sort.unsorted()
        }

        "a request turns into a query with both halves on it" {
            val query = request("/orders?filter=status:eq:PAID&sort=total:DESC").mongoQuery

            query.queryObject.toJson() shouldContain "PAID"
            query.sortObject.toJson() shouldContain "\"total\": -1"
        }

        "a request with neither is a query that excludes and orders nothing" {
            val query = request("/orders").mongoQuery

            query.queryObject.isEmpty() shouldBe true
            query.sortObject.isEmpty() shouldBe true
        }

        "paging is not applied, because which kind of paging is the route's decision" {
            // limit/skip on a query is offset paging, and a keyset cursor is the other answer.
            request("/orders?page=2&size=10").mongoQuery.limit shouldBe 0
        }
    })
