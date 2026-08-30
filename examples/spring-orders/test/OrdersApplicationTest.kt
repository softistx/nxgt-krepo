package com.strange.example.orders

import com.strange.example.orders.api.apis.IHealthService
import com.strange.spring.client.withClient
import com.strange.spring.testing.MongoSpec
import com.strange.spring.testing.awaitMigrations
import com.strange.spring.testing.clear
import com.strange.spring.testing.mongoAvailable
import com.strange.spring.testing.webTestClient
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.Json
import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import kotlin.time.Duration.Companion.seconds

/**
 * What a typed client cannot say — asserted on the wire, with a `WebTestClient`.
 *
 * `OrderControllerTest` drives the endpoints through the generated interfaces, which proves the
 * *contract*: the types, the statuses, the typed failures. It cannot prove the things the contract
 * has no name for, and they are most of what this module is a demonstration of — the envelope's JSON
 * shape, the translated message text, a status the document never declared, and the
 * auto-configurations that turn a plain `@SpringBootApplication` into this one.
 */
class OrdersApplicationTest(
    template: ReactiveMongoTemplate,
    json: Json,
) : MongoSpec({

        val web = webTestClient()

        // The second tag, off this spec's own factory.
        val health = apiFactory(json).withClient<IHealthService>()

        beforeSpec { if (mongoAvailable) template.awaitMigrations(expected = 2) }
        beforeEach { if (mongoAvailable) template.clear("orders", "audits") }

        feature("the application boots into a working state").config(enabled = mongoAvailable) {
            scenario("a route answers, encoded by the kotlinx codecs stx.json installed") {
                web
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
                // Settled by `awaitMigrations`, which is where the polling lives: `MigrationRunner`
                // listens for `ApplicationReadyEvent` and suspends, and Spring does not wait for a
                // suspending listener — so the records appear shortly after the port opens rather
                // than with it. Worth knowing before making a migration a startup gate: it is not one.
                val applied =
                    template
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
        }

        feature("failures come back translated").config(enabled = mongoAvailable) {
            scenario("a missing order is a 404 carrying the key as its code") {
                web
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
                web
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
                web
                    .get()
                    .uri("/orders?filter=status:nonsense:PAID")
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }
        }

        feature("writing an order").config(enabled = mongoAvailable) {
            scenario("a placed order comes back with an id and is readable by it") {
                val body =
                    web
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

                // `placedAt` is a `kotlin.time.Instant`. Without `stx.data.mongo.enabled` this read
                // is where it fails, with `Can't find a codec` — not at insert time, and not at
                // startup, which is what makes the one line in the yaml worth a scenario.
                web
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
                    web
                        .post()
                        .uri("/orders")
                        .bodyValue(mapOf("reference" to "B-2002", "customer" to "ada", "total" to 900))
                        .exchange()
                        .expectStatus()
                        .isCreated
                        .expectBody()
                        .returnResult()
                        .responseBody!!
                        .decodeToString()
                        .let { Regex("\"id\":\"([0-9a-f]{24})\"").find(it)!!.groupValues[1] }

                web
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

        feature("paging through the whole result set").config(enabled = mongoAvailable) {
            scenario("every order is seen exactly once, one page at a time") {
                // The defect this pins: with the ordering on the query instead of on the window,
                // the first page looks right and the second comes back empty.
                listOf("P-4500" to 4_500, "P-7500" to 7_500, "P-24990" to 24_990, "P-132000" to 132_000)
                    .forEach { (reference, total) ->
                        web
                            .post()
                            .uri("/orders")
                            .bodyValue(mapOf("reference" to reference, "customer" to "ada", "total" to total))
                            .exchange()
                            .expectStatus()
                            .isCreated
                    }

                val seen = mutableListOf<String>()
                var cursor: String? = null
                repeat(4) {
                    val page =
                        web
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
                seen shouldContainExactly listOf("P-4500", "P-7500", "P-24990", "P-132000")
            }
        }
    })
