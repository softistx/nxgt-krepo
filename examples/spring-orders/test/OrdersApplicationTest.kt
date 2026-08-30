package com.strange.example.orders

import com.strange.example.orders.api.apis.IHealthService
import com.strange.spring.client.withClient
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.reactor.awaitSingle
import org.bson.Document
import org.springframework.data.mongodb.core.query.Query
import kotlin.time.Duration.Companion.seconds

/**
 * What a typed client cannot say — asserted on the wire, with a `WebTestClient`.
 *
 * `OrderControllerTest` drives the endpoints through the generated interfaces, which proves the
 * *contract*: the types, the statuses, the typed failures. It cannot prove the things the contract
 * has no name for, and they are most of what this module is a demonstration of — the envelope's JSON
 * shape, the translated message text, a status the document never declared, and the four
 * auto-configurations that turn a plain `@SpringBootApplication` into this one.
 *
 * `bindToServer` and not `bindToApplicationContext`: the kotlinx codecs, the exception advice, the
 * locale negotiation, the `kotlin.time.Instant` converters, the index creation and the migration run
 * are all auto-configurations, and a client bound to a context bypasses several of them.
 */
class OrdersApplicationTest :
    FeatureSpec({

        val api = OrdersApi()
        lateinit var health: IHealthService

        beforeSpec {
            if (!mongo.available) return@beforeSpec
            api.start()
            // The second tag, off the same factory `OrderControllerTest` takes `IOrdersService` from.
            health = api.factory.withClient()
            api.ready()
        }

        afterSpec { api.stop() }

        feature("the application boots into a working state").config(enabled = mongo.available) {
            scenario("a route answers, encoded by the kotlinx codecs stx.json installed") {
                api.web
                    .get()
                    .uri("/health")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.data.status")
                    .isEqualTo("UP")

                // The same endpoint through the interface `HealthController` implements. One says
                // the envelope has `data.status`; the other says the document's `Health` decodes.
                health.health().data.status shouldBe "UP"
            }

            scenario("both migrations ran, once, in order") {
                // Settled by `OrdersApi.ready()`, which is where the polling lives: `MigrationRunner`
                // listens for `ApplicationReadyEvent` and suspends, and Spring does not wait for a
                // suspending listener — so the records appear shortly after the port opens rather
                // than with it. Worth knowing before making a migration a startup gate: it is not one.
                val applied =
                    api.template
                        .findAll(Document::class.java, "migrations")
                        .collectList()
                        .awaitSingle()
                        .sortedBy { it.getInteger("order") }

                applied.map { it.getString("status") } shouldContainExactly listOf("APPLIED", "APPLIED")
                // `code` is `<prefix><order>` — the identity of a migration, and what the unique
                // index is on. The class name is where it comes from, not what is stored.
                applied.map { it.getString("code") } shouldContainExactly listOf("V1", "V2")
                applied.first().getString("description") shouldBe "seeds three demo orders"
            }

            scenario("the seed migration's three orders are readable, which needs the Instant converters") {
                // `placedAt` is a `kotlin.time.Instant`. Without `stx.data.mongo.enabled` this is
                // where it fails — at read time, with `Can't find a codec`, not at insert time.
                api.web
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
                api.web
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
                api.web
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
                // A status the document does not declare for this operation, which is exactly why it
                // is asserted here: a generated client has no name for an undocumented failure.
                api.web
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
                    api.web
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

                api.web
                    .get()
                    .uri("/orders/$id")
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.data.customer")
                    .isEqualTo("hopper")
            }

            scenario("a save appends to the audit trail") {
                val id =
                    api.template
                        .findOne(Query(), Document::class.java, "orders")
                        .awaitSingle()
                        .getObjectId("_id")
                        .toHexString()

                api.web
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
                    api.template.count(Query(), "audits").awaitSingle() shouldBeGreaterThan 0L
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
                        api.web
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
                // By total, ascending: A-1002 4500, B-2001 7500, A-1001 24990, A-1003 132000.
                seen shouldContainExactly listOf("A-1002", "B-2001", "A-1001", "A-1003")
            }
        }
    })
