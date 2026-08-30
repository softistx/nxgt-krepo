package com.strange.example.orders

import com.strange.example.orders.api.apis.IOrdersService
import com.strange.example.orders.api.models.ChangeStatus
import com.strange.example.orders.api.models.OrderStatus
import com.strange.example.orders.api.models.PlaceOrder
import com.strange.example.orders.api.utils.ErrorResponseException
import com.strange.spring.client.withClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * `OrderController` end to end, through the interface it implements.
 *
 * **Nothing here builds a request.** Every scenario calls `IOrdersService` — the interface generated
 * from `openapi/`, the one `OrderController` implements — over a client built by `stx-spring-boot`'s
 * `httpServiceFactory` against the running server. The paths, the verbs, the query parameters, the
 * status codes and the body types are all the document's; a spec that spelled a URL again would be
 * asserting against its own copy of the contract rather than against the contract.
 *
 * Features are named after the route, as in `nxgt-rest`: a failure names the endpoint that broke,
 * and the list of features reads as the surface the document declares. What the document declares as
 * a failure is asserted as one — `shouldThrow<ErrorResponseException>` on the status and the `code`,
 * never on the message, which is translated and changes the day somebody improves a sentence.
 *
 * `OrdersApplicationTest` holds the claims a typed client cannot make: the envelope's JSON shape,
 * the translated text, and a status the document does not declare.
 */
class OrderControllerTest :
    FeatureSpec({

        val api = OrdersApi()
        lateinit var orders: IOrdersService

        beforeSpec {
            if (!mongo.available) return@beforeSpec
            api.start()
            // One factory, one client per tag: `OrdersApplicationTest` takes `IHealthService` off
            // the same one. Rebuilding it per interface would rebuild the WebClient, and with it
            // every codec and filter a generated client needs.
            orders = api.factory.withClient()
            api.ready()
        }

        afterSpec { api.stop() }

        feature("GET /orders").config(enabled = mongo.available) {
            scenario("a page carries its rows and its cursors") {
                val page = orders.findOrders(size = 1)

                page.data shouldHaveSize 1
                page.metadata.hasNextPage shouldBe true
                page.metadata.endCursor.shouldNotBeNull()
            }

            scenario("the cursor resumes where the page ended") {
                val first = orders.findOrders(sort = "total:ASC", size = 1)
                val second = orders.findOrders(sort = "total:ASC", size = 1, cursor = first.metadata.endCursor)

                second.data.single().id shouldNotBe first.data.single().id
            }
        }

        feature("GET /orders/valuable").config(enabled = mongo.available) {
            scenario("only paid orders at or above the floor come back") {
                val valuable = orders.valuableOrders(floor = 10_000).data

                // The seeds: A-1001 is PAID at 24 990 and A-1003 PAID at 132 000; A-1002 is PENDING.
                valuable.map { it.reference } shouldContainExactlyInAnyOrder listOf("A-1001", "A-1003")
                valuable.forEach {
                    it.status shouldBe OrderStatus.PAID
                    it.total shouldBeGreaterThanOrEqual 10_000
                }
            }
        }

        feature("POST /orders").config(enabled = mongo.available) {
            scenario("a placed order comes back with the id it was given") {
                val placed = orders.placeOrder(PlaceOrder(reference = "C-3001", customer = "lovelace", total = 12_000))

                placed.data.reference shouldBe "C-3001"
                placed.data.status shouldBe OrderStatus.PENDING
                placed.data.id.shouldNotBeNull()
            }

            scenario("a reference already taken is the 409 the document declares") {
                shouldThrow<ErrorResponseException> {
                    orders.placeOrder(PlaceOrder(reference = "C-3001", customer = "someone else", total = 1))
                }.error.code shouldBe "orders.reference-taken"
            }
        }

        feature("GET /orders/{id}").config(enabled = mongo.available) {
            scenario("an order is readable by the id its placement returned") {
                val placed = orders.placeOrder(PlaceOrder(reference = "C-3002", customer = "hopper", total = 7_500))

                val read = orders.findOrder(placed.data.id).data

                read.customer shouldBe "hopper"
                read.total shouldBe 7_500
                // `placedAt` is a `kotlin.time.Instant` on both sides — the whole reason this
                // client's WebClient is on kotlinx codecs rather than the default Jackson ones,
                // which have never heard of the type.
                //
                // Compared to the millisecond, and not to `placed`: BSON stores a date to the
                // millisecond, so the value handed back by the write still carries the nanoseconds
                // the JVM generated and the stored one never will.
                read.placedAt.toEpochMilliseconds() shouldBe placed.data.placedAt.toEpochMilliseconds()
            }

            scenario("a documented failure arrives as the exception the document describes") {
                // Not a WebClientResponseException carrying an unparsed body: `apiErrorFilter` read
                // the document's 404 response, and `error` is the same `ErrorResponse` the server sent.
                val thrown = shouldThrow<ErrorResponseException> { orders.findOrder("000000000000000000000000") }

                thrown.status shouldBe 404
                thrown.error.code shouldBe "orders.not-found"
            }
        }

        feature("PATCH /orders/{id}/status").config(enabled = mongo.available) {
            scenario("an enum argument goes out as the document spells it") {
                val id = orders.placeOrder(PlaceOrder(reference = "C-3003", customer = "clarke", total = 100)).data.id

                orders.changeStatus(id, ChangeStatus(status = OrderStatus.SHIPPED)).data.status shouldBe
                    OrderStatus.SHIPPED
            }
        }

        feature("DELETE /orders/{id}").config(enabled = mongo.available) {
            scenario("cancelling one leaves it gone") {
                val id = orders.placeOrder(PlaceOrder(reference = "C-3004", customer = "noether", total = 10)).data.id

                orders.cancelOrder(id)

                shouldThrow<ErrorResponseException> { orders.findOrder(id) }.status shouldBe 404
            }
        }
    })
