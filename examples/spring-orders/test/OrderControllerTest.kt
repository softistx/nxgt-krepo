package com.softistx.example.orders

import com.softistx.example.orders.api.Endpoints
import com.softistx.example.orders.api.apis.IOrdersService
import com.softistx.example.orders.api.models.ChangeStatusRequest
import com.softistx.example.orders.api.models.OrderStatus
import com.softistx.example.orders.api.models.PlaceOrderRequest
import com.softistx.example.orders.api.utils.ErrorResponseException
import com.softistx.spring.client.withClient
import com.softistx.spring.testing.MongoSpec
import com.softistx.spring.testing.clear
import com.softistx.spring.testing.mongoAvailable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

/**
 * `OrderController` end to end, through the interface it implements.
 *
 * **Nothing here builds a request.** Every scenario calls `IOrdersService` — the interface generated
 * from `openapi/`, the one `OrderController` implements — over a client pointed at the running
 * application. The paths, the verbs, the query parameters, the status codes and the body types are
 * all the document's; a spec that spelled a URL again would be asserting against its own copy of the
 * contract rather than against the contract.
 *
 * Features are named after the route, as in `nxgt-rest`: a failure names the endpoint that broke,
 * and the list of features reads as the surface the document declares. What the document declares as
 * a failure is asserted as one — `shouldThrow<ErrorResponseException>` on the status and the `code`,
 * never on the message, which is translated and changes the day somebody improves a sentence.
 *
 * The application and the beans are Spring's doing: `MongoSpec` carries the annotations, and what
 * this class asks for in its constructor is autowired into it. `OrdersApplicationTest` holds the
 * claims a typed client cannot make.
 */
class OrderControllerTest(
    template: ReactiveMongoTemplate,
    json: Json,
) : MongoSpec({

        // One factory, one client per tag: `OrdersApplicationTest` takes `IHealthService` off its
        // own. Rebuilding it per interface rebuilds the WebClient, and with it every codec and
        // filter a generated client needs.
        val orders = apiFactory(json).withClient<IOrdersService>()

        // Each scenario writes what it reads, so none of them depends on another having run.
        beforeEach { if (mongoAvailable) template.clear("orders", "audits") }

        feature(Endpoints.GET_ORDERS.label).config(enabled = mongoAvailable) {
            scenario("a page carries its rows and its cursors") {
                repeat(3) { orders.placeOrder(PlaceOrderRequest(reference = "P-$it", customer = "ada", total = 100L + it)) }

                val page = orders.findOrders(size = 1)

                page.data shouldHaveSize 1
                page.metadata.hasNextPage shouldBe true
                page.metadata.endCursor.shouldNotBeNull()
            }

            scenario("the cursor resumes where the page ended") {
                repeat(3) { orders.placeOrder(PlaceOrderRequest(reference = "P-$it", customer = "ada", total = 100L + it)) }

                val first = orders.findOrders(sort = "total:ASC", size = 1)
                val second = orders.findOrders(sort = "total:ASC", size = 1, cursor = first.metadata.endCursor)

                second.data.single().id shouldNotBe first.data.single().id
            }
        }

        feature(Endpoints.GET_ORDERS_VALUABLE.label).config(enabled = mongoAvailable) {
            scenario("only paid orders at or above the floor come back") {
                val big = orders.placeOrder(PlaceOrderRequest(reference = "V-1", customer = "ada", total = 132_000)).data.id
                val small = orders.placeOrder(PlaceOrderRequest(reference = "V-2", customer = "grace", total = 4_500)).data.id
                // Placed orders are PENDING; the endpoint filters on PAID, so two of these three are
                // out for a different reason each — one on its status, one on its total.
                orders.placeOrder(PlaceOrderRequest(reference = "V-3", customer = "ada", total = 24_990))
                orders.changeStatus(big, ChangeStatusRequest(status = OrderStatus.PAID))
                orders.changeStatus(small, ChangeStatusRequest(status = OrderStatus.PAID))

                val valuable = orders.valuableOrders(floor = 10_000).data

                valuable.map { it.reference } shouldContainExactlyInAnyOrder listOf("V-1")
                valuable.forEach {
                    it.status shouldBe OrderStatus.PAID
                    it.total shouldBeGreaterThanOrEqual 10_000
                }
            }
        }

        feature(Endpoints.POST_ORDERS.label).config(enabled = mongoAvailable) {
            scenario("a placed order comes back with the id it was given") {
                val placed = orders.placeOrder(PlaceOrderRequest(reference = "C-3001", customer = "lovelace", total = 12_000))

                placed.data.reference shouldBe "C-3001"
                placed.data.status shouldBe OrderStatus.PENDING
                placed.data.id.shouldNotBeNull()
            }

            scenario("a reference already taken is the 409 the document declares") {
                orders.placeOrder(PlaceOrderRequest(reference = "C-3001", customer = "lovelace", total = 12_000))

                shouldThrow<ErrorResponseException> {
                    orders.placeOrder(PlaceOrderRequest(reference = "C-3001", customer = "someone else", total = 1))
                }.error.code shouldBe "orders.reference-taken"
            }
        }

        feature(Endpoints.GET_ORDERS_ID.label).config(enabled = mongoAvailable) {
            scenario("an order is readable by the id its placement returned") {
                val placed = orders.placeOrder(PlaceOrderRequest(reference = "C-3002", customer = "hopper", total = 7_500))

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

        feature(Endpoints.PATCH_ORDERS_ID_STATUS.label).config(enabled = mongoAvailable) {
            scenario("an enum argument goes out as the document spells it") {
                val id = orders.placeOrder(PlaceOrderRequest(reference = "C-3003", customer = "clarke", total = 100)).data.id

                orders.changeStatus(id, ChangeStatusRequest(status = OrderStatus.SHIPPED)).data.status shouldBe
                    OrderStatus.SHIPPED
            }
        }

        feature(Endpoints.DELETE_ORDERS_ID.label).config(enabled = mongoAvailable) {
            scenario("cancelling one leaves it gone") {
                val id = orders.placeOrder(PlaceOrderRequest(reference = "C-3004", customer = "noether", total = 10)).data.id

                orders.cancelOrder(id)

                shouldThrow<ErrorResponseException> { orders.findOrder(id) }.status shouldBe 404
            }
        }
    })
