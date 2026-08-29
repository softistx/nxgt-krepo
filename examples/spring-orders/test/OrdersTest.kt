package com.strange.example.orders

import com.strange.testing.containers.mongoContainer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
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
 * The whole application over HTTP, on a real port, against a real MongoDB.
 *
 * `WebTestClient.bindToServer` and not `bindToRouterFunction`, because most of what is worth
 * asserting here is *not* in the routes: the kotlinx codecs, the exception advice, the locale
 * negotiation, the `kotlin.time.Instant` converters, the index creation and the migration run are
 * all auto-configurations, and a test that binds to a router function bypasses every one of them.
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

        beforeSpec {
            if (!mongo.available) return@beforeSpec
            val database = "spring_orders_test_${databases.incrementAndGet()}"
            val context =
                SpringApplicationBuilder(OrdersApplication::class.java)
                    .web(WebApplicationType.REACTIVE)
                    .properties(
                        "server.port=0",
                        "spring.data.mongodb.uri=${mongo.endpoint}",
                        "spring.data.mongodb.database=$database",
                        // A test suite should not pass over a translation nobody wrote.
                        "stx.i18n.fail-on-missing-key=true",
                    ).run() as ReactiveWebServerApplicationContext
            app = context
            template = context.getBean(ReactiveMongoTemplate::class.java)
            client =
                WebTestClient
                    .bindToServer()
                    .baseUrl("http://localhost:${context.webServer!!.port}")
                    .build()
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
    })
