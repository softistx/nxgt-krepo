package com.strange.example.orders

import com.strange.example.orders.api.apis.IHealthService
import com.strange.example.orders.api.apis.IOrdersService
import com.strange.example.orders.api.models.ChangeStatus
import com.strange.example.orders.api.models.OrderStatus
import com.strange.example.orders.api.models.PlaceOrder
import com.strange.example.orders.api.utils.ErrorResponseException
import com.strange.testing.containers.mongoContainer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.Json
import org.bson.Document
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.reactive.context.ReactiveWebServerApplicationContext
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

private val mongo = mongoContainer()

private val databases = AtomicInteger()

/**
 * [this] with its database replaced by [name].
 *
 * **Not `spring.data.mongodb.database`.** Boot reads that property only when it is building a
 * connection string from `host`/`port`; once `spring.data.mongodb.uri` is set the database comes
 * from the URI and the property is ignored, silently. `MongoDBContainer` hands back a URL ending in
 * `/test` and a `MONGO_TEST_URI` naming the workspace's replica set usually ends in no database at
 * all — so both spellings were landing every run in one shared database, and the isolation this
 * spec's KDoc claims was not happening. What surfaced it was a manual `./kotlin run` against the
 * same server leaving rows the paging scenario then counted.
 */
private fun String?.withDatabase(name: String): String {
    // Non-null by the time this is called: `beforeSpec` returns early unless the service resolved.
    val uri = requireNotNull(this) { "no MongoDB endpoint" }
    val query = uri.substringAfter("?", "").let { if (it.isEmpty()) "" else "?$it" }
    val base = uri.substringBefore("?").trimEnd('/')
    // "mongodb://host:port" has two slashes; a third one starts the database path.
    val host = if (base.count { it == '/' } > 2) base.substringBeforeLast('/') else base
    return "$host/$name$query"
}

/**
 * The whole application over HTTP, on a real port, against a real MongoDB.
 *
 * `WebTestClient.bindToServer` and not `bindToApplicationContext`, because most of what is worth
 * asserting here is *not* in the controllers: the kotlinx codecs, the exception advice, the locale
 * negotiation, the `kotlin.time.Instant` converters, the index creation and the migration run are
 * all auto-configurations, and a test that binds to a context bypasses several of them.
 *
 * The last feature drives the same endpoints through `IOrdersService` — the generated interface the
 * controller implements — built into a client by `HttpServiceProxyFactory`. That is the whole claim
 * of a spec-first API stated as a test: one document, one interface, and the server and the client
 * cannot disagree about it because neither of them wrote it.
 *
 * A database of its own per run, dropped afterwards — `MONGO_TEST_URI` usually names the workspace's
 * own replica set, and a run that reuses a server has to leave it as it found it.
 */
class OrdersTest :
    FeatureSpec({

        // Held so `afterSpec` can close the context and drop the database even when a scenario fails.
        var app: ReactiveWebServerApplicationContext? = null
        lateinit var client: WebTestClient
        lateinit var template: ReactiveMongoTemplate

        // The same interfaces the controllers implement, this time as clients.
        lateinit var orders: IOrdersService
        lateinit var health: IHealthService

        beforeSpec {
            if (!mongo.available) return@beforeSpec
            val database = "spring_orders_test_${databases.incrementAndGet()}"
            val context =
                SpringApplicationBuilder(OrdersApplication::class.java)
                    .web(WebApplicationType.REACTIVE)
                    // Arguments and not `.properties()`: that method contributes Boot's *default*
                    // property source, the lowest-precedence one there is, so
                    // `resources/application.yaml` won every key it also names — including
                    // `spring.data.mongodb.uri`.
                    .run(
                        "--server.port=0",
                        "--spring.data.mongodb.uri=${mongo.endpoint.withDatabase(database)}",
                        // A test suite should not pass over a translation nobody wrote.
                        "--stx.i18n.fail-on-missing-key=true",
                    ) as ReactiveWebServerApplicationContext
            app = context
            template = context.getBean(ReactiveMongoTemplate::class.java)
            val baseUrl = "http://localhost:${context.webServer!!.port}"
            client = WebTestClient.bindToServer().baseUrl(baseUrl).build()

            // The generated interfaces, as clients. `ApiClients.kt` has the three things that
            // have to be true for the proxy to behave and the reason each one is not a build error.
            val factory = apiFactory(baseUrl, context.getBean("stxWebJson", Json::class.java))
            orders = factory.client()
            health = factory.client()
        }

        afterSpec {
            app?.let { context ->
                runCatching {
                    context
                        .getBean(ReactiveMongoTemplate::class.java)
                        .mongoDatabase
                        .awaitSingle()
                        .drop()
                        .awaitFirstOrNull()
                }
                context.close()
            }
        }

        feature("the application boots into a working state").config(enabled = mongo.available) {
            scenario("a route answers, encoded by the kotlinx codecs stx.json installed") {
                client
                    .get()
                    .uri("/health")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.data.status")
                    .isEqualTo("UP")
            }

            scenario("both migrations ran, once, in order") {
                // `MigrationRunner` listens for `ApplicationReadyEvent` and suspends, and Spring does
                // not wait for a suspending listener — `publishEvent` returns while it is still
                // running. So the records appear shortly after the context is up rather than with it,
                // and this polls rather than assuming. That is worth knowing before making a
                // migration a startup gate: it is not one.
                val applied =
                    eventually(10.seconds) {
                        val records =
                            template
                                .findAll(Document::class.java, "migrations")
                                .collectList()
                                .awaitSingle()
                                .sortedBy { it.getInteger("order") }
                        records.map { it.getString("status") } shouldContainExactly listOf("APPLIED", "APPLIED")
                        records
                    }

                // `code` is `<prefix><order>` — the identity of a migration, and what the unique
                // index is on. The class name is where it comes from, not what is stored.
                applied.map { it.getString("code") } shouldContainExactly listOf("V1", "V2")
                applied.first().getString("description") shouldBe "seeds three demo orders"
            }

            scenario("the seed migration's three orders are readable, which needs the Instant converters") {
                // `placedAt` is a `kotlin.time.Instant`. Without `stx.data.mongo.enabled` this is
                // where it fails — at read time, with `Can't find a codec`, not at insert time.
                client
                    .get()
                    .uri("/orders?size=10")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.data.length()")
                    .isEqualTo(3)
            }
        }

        feature("failures come back translated").config(enabled = mongo.available) {
            scenario("a missing order is a 404 carrying the key as its code") {
                client
                    .get()
                    .uri("/orders/000000000000000000000000")
                    .exchange()
                    .expectStatus()
                    .isNotFound
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo("orders.not-found")
                    .jsonPath("$.message")
                    .value<String> { it shouldContain "No order with id" }
            }

            scenario("the same failure answers in the caller's language") {
                client
                    .get()
                    .uri("/orders/000000000000000000000000")
                    .header("Accept-Language", "fr")
                    .exchange()
                    .expectStatus()
                    .isNotFound
                    .expectBody()
                    .jsonPath("$.message")
                    .value<String> { it shouldContain "Aucune commande" }
            }

            scenario("a filter nobody can read is a 400, not a collection scan") {
                client
                    .get()
                    .uri("/orders?filter=status:nonsense:PAID")
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }
        }

        feature("writing an order").config(enabled = mongo.available) {
            scenario("a placed order comes back with an id and is readable by it") {
                val body =
                    client
                        .post()
                        .uri("/orders")
                        .bodyValue(mapOf("reference" to "B-2001", "customer" to "hopper", "total" to 7_500))
                        .exchange()
                        .expectStatus()
                        .isCreated
                        .expectBody()
                        .jsonPath("$.data.reference")
                        .isEqualTo("B-2001")
                        .returnResult()
                        .responseBody!!
                        .decodeToString()

                val id = Regex("\"id\":\"([0-9a-f]{24})\"").find(body)!!.groupValues[1]

                client
                    .get()
                    .uri("/orders/$id")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.data.customer")
                    .isEqualTo("hopper")
            }

            scenario("the same reference twice is a 409") {
                client
                    .post()
                    .uri("/orders")
                    .bodyValue(mapOf("reference" to "B-2001", "customer" to "someone else", "total" to 1))
                    .exchange()
                    .expectStatus()
                    .isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo("orders.reference-taken")
            }

            scenario("a save appends to the audit trail") {
                val id =
                    template
                        .findOne(Query(), Document::class.java, "orders")
                        .awaitSingle()
                        .getObjectId("_id")
                        .toHexString()

                client
                    .patch()
                    .uri("/orders/$id/status")
                    .bodyValue(mapOf("status" to "SHIPPED"))
                    .exchange()
                    .expectStatus()
                    .isOk

                // The trail is written on a scope of its own — `AuditListener` returns before the
                // entry is stored — so the entry lands shortly after the response rather than with
                // it. Poll for it; a fixed sleep is either flaky or slow, and usually both.
                eventually(5.seconds) {
                    template.count(Query(), "audits").awaitSingle() shouldBeGreaterThan 0L
                }
            }
        }

        feature("paging through the whole result set").config(enabled = mongo.available) {
            scenario("every order is seen exactly once, one page at a time") {
                // The defect this pins: with the ordering on the query instead of on the window,
                // the first page looks right and the second comes back empty.
                val seen = mutableListOf<String>()
                var cursor: String? = null
                repeat(4) {
                    val page =
                        client
                            .get()
                            .uri("/orders?sort=total:ASC&size=1${cursor?.let { c -> "&cursor=$c" } ?: ""}")
                            .exchange()
                            .expectStatus()
                            .isOk
                            .expectBody()
                            .returnResult()
                            .responseBody!!
                            .decodeToString()

                    seen += Regex("\"reference\":\"([^\"]+)\"").findAll(page).map { m -> m.groupValues[1] }
                    cursor = Regex("\"endCursor\":\"([^\"]+)\"").find(page)?.groupValues?.get(1)
                }

                seen shouldHaveSize 4
                seen.toSet() shouldHaveSize 4
                // By total, ascending: 1 (B-2001 7500), A-1002 4500 … see the seeds.
                seen shouldContainExactly listOf("A-1002", "B-2001", "A-1001", "A-1003")
            }
        }

        // Named after the route, as in nxgt-rest: a failure names the endpoint that broke, and the
        // list of features reads as the surface the document declares.
        feature("GET /health, through IHealthService").config(enabled = mongo.available) {
            scenario("answers UP") {
                health.health().data.status shouldBe "UP"
            }
        }

        feature("POST /orders, through IOrdersService").config(enabled = mongo.available) {
            scenario("a placed order comes back with the id it was given") {
                val placed = orders.placeOrder(PlaceOrder(reference = "C-3001", customer = "lovelace", total = 12_000))

                placed.data.reference shouldBe "C-3001"
                placed.data.status shouldBe OrderStatus.PENDING

                val read = orders.findOrder(placed.data.id).data
                read.customer shouldBe "lovelace"
                read.total shouldBe 12_000
                // `placedAt` is a `kotlin.time.Instant` on both sides — the whole reason this
                // client's WebClient is on kotlinx codecs rather than the default Jackson ones,
                // which have never heard of the type.
                //
                // Compared to the millisecond, and not to `placed`: BSON stores a date to the
                // millisecond, so the value handed back by the write still carries the nanoseconds
                // the JVM generated and the stored one never will.
                read.placedAt.toEpochMilliseconds() shouldBe placed.data.placedAt.toEpochMilliseconds()
            }

            scenario("a reference already taken is the 409 the document declares") {
                shouldThrow<ErrorResponseException> {
                    orders.placeOrder(PlaceOrder(reference = "C-3001", customer = "someone else", total = 1))
                }.error.code shouldBe "orders.reference-taken"
            }
        }

        feature("GET /orders, through IOrdersService").config(enabled = mongo.available) {
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

        feature("GET /orders/valuable, through IOrdersService").config(enabled = mongo.available) {
            scenario("only paid orders at or above the floor come back") {
                orders.valuableOrders(floor = 10_000).data.forEach {
                    it.status shouldBe OrderStatus.PAID
                    it.total shouldBeGreaterThanOrEqual 10_000
                }
            }
        }

        feature("PATCH /orders/{id}/status, through IOrdersService").config(enabled = mongo.available) {
            scenario("an enum argument goes out as the document spells it") {
                val id =
                    orders
                        .findOrders(size = 1)
                        .data
                        .first()
                        .id

                orders.changeStatus(id, ChangeStatus(status = OrderStatus.SHIPPED)).data.status shouldBe
                    OrderStatus.SHIPPED
            }
        }

        feature("GET and DELETE /orders/{id}, through IOrdersService").config(enabled = mongo.available) {
            scenario("a documented failure arrives as the exception the document describes") {
                // Not a WebClientResponseException carrying an unparsed body: `apiErrorFilter` read
                // the document's 404 response, and `error` is the same `ErrorResponse` the server sent.
                val thrown = shouldThrow<ErrorResponseException> { orders.findOrder("000000000000000000000000") }

                thrown.status shouldBe 404
                thrown.error.code shouldBe "orders.not-found"
            }

            scenario("cancelling one leaves it gone") {
                val id = orders.placeOrder(PlaceOrder(reference = "C-3002", customer = "clarke", total = 10)).data.id

                orders.cancelOrder(id)

                shouldThrow<ErrorResponseException> { orders.findOrder(id) }.status shouldBe 404
            }
        }
    })
