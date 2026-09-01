package com.softistx.example.orders

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.softistx.example.orders.api.Endpoints
import com.softistx.example.orders.api.apis.IHealthService
import com.softistx.example.orders.api.path
import com.softistx.example.orders.migration.V1Seed
import com.softistx.example.orders.model.Order
import com.softistx.example.orders.model.OrderStatus
import com.softistx.spring.client.withClient
import com.softistx.spring.testing.MongoSpec
import com.softistx.spring.testing.clear
import com.softistx.spring.testing.mongoAvailable
import com.softistx.spring.testing.webTestClient
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.Json
import org.bson.Document
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import kotlin.time.Duration.Companion.seconds

/** A well-formed ObjectId that no document carries, for the 404 paths. */
private const val MISSING_ID = "000000000000000000000000"

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
    database: MongoDatabase,
    json: Json,
) : MongoSpec({

        val web = webTestClient()

        // The second tag, off this spec's own factory.
        val health = apiFactory(json).withClient<IHealthService>()

        beforeEach { if (mongoAvailable) template.clear("orders", "audits") }

        feature("the application boots into a working state").config(enabled = mongoAvailable) {
            scenario("a route answers, encoded by the kotlinx codecs stx.json installed") {
                web
                    .get()
                    .uri(Endpoints.GET_HEALTH.value)
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

            scenario("both migrations ran, once, in order, before this spec could look") {
                // **Nothing waits here, and that is the assertion.** The previous runner was a
                // suspending `@EventListener(ApplicationReadyEvent)` and Spring does not wait for
                // one, so the records appeared shortly *after* the port opened and every spec in
                // this module had to poll for them first. `MigrationGate` is an `InitializingBean`:
                // the context is not refreshed until the ledger says APPLIED, so by the time this
                // spec has a `WebTestClient` at all the answer is already on disk.
                val applied =
                    template
                        .findAll(Document::class.java, "stx_migrations")
                        .collectList()
                        .awaitSingle()
                        .sortedBy { it.getLong("_id") }

                // The `_id` is the version the class declares, which is what makes a rename free.
                applied.map { it.getLong("_id") } shouldContainExactly listOf(1L, 2L)
                applied.map { it.getString("status") } shouldContainExactly listOf("APPLIED", "APPLIED")
                applied.first().getString("description") shouldBe "seeds three demo orders"
                // Who ran it — host, pid and a random suffix. The prior art recorded no such thing,
                // which made a half-applied deploy impossible to attribute.
                applied.forEach { it.getString("appliedBy").shouldNotBeNull() }
            }

            scenario("the seed migration's documents read back through the application's mapping") {
                // Applied again, by hand, into the collection `beforeEach` has just emptied — which
                // is both how this claim is made deterministic (the seed the gate wrote at startup
                // belongs to whichever spec ran first) and a demonstration of what the library asks
                // of every migration: running it twice is allowed to be boring.
                //
                // `database` is the bridge bean: a coroutine `MongoDatabase` over the pool Spring
                // Data opened, pointed at this run's database. That it can be injected here at all
                // is what `stx.migrations.store: mongo` depends on.
                V1Seed().migrate(database)

                val seeded =
                    template
                        .findAll(Order::class.java)
                        .collectList()
                        .awaitSingle()
                        .sortedBy { it.reference }

                // The migrations write raw documents and the application reads mapped ones, so this
                // is what catches a field name, a BSON type or an `_id` written in a shape `Order`
                // cannot be read back from. `V1Seed` writes `ref`, not `reference`, and a BSON date,
                // not a string — a string would insert cleanly and fail exactly here.
                seeded.map { it.reference } shouldContainExactly listOf("A-1001", "A-1002", "A-1003")
                seeded.map { it.status } shouldContainExactly
                    listOf(OrderStatus.PAID, OrderStatus.PENDING, OrderStatus.PAID)
                seeded.map { it.total } shouldContainExactly listOf(24_990L, 4_500L, 132_000L)
                seeded.forEach { it.tags shouldBe emptyList() }
                seeded.forEach { it.placedAt.toEpochMilliseconds() shouldBeGreaterThan 0L }
            }

            scenario("the lock was taken in a collection of its own, and released") {
                val locks =
                    template
                        .findAll(Document::class.java, "stx_migrations_lock")
                        .collectList()
                        .awaitSingle()

                // A sentinel document beside the records would be one `find()` away from reading as
                // a version that has run, which is why the lock is not in `stx_migrations`.
                locks shouldHaveSize 1
                // Released, because the run finished. A `lockedBy` still set here would be a
                // watchdog that outlived its work and a second instance locked out for a lease.
                locks.first().get("lockedBy") shouldBe null
            }
        }

        feature("failures come back translated").config(enabled = mongoAvailable) {
            scenario("a missing order is a 404 carrying the key as its code") {
                web
                    .get()
                    .uri(Endpoints.GET_ORDERS_ID.path(MISSING_ID))
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
                    .uri(Endpoints.GET_ORDERS_ID.path(MISSING_ID))
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
                    .uri("${Endpoints.GET_ORDERS.value}?filter=status:nonsense:PAID")
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
                        .uri(Endpoints.GET_ORDERS.value)
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
                    .uri(Endpoints.GET_ORDERS_ID.path(id))
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
                        .uri(Endpoints.GET_ORDERS.value)
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
                    .uri(Endpoints.PATCH_ORDERS_ID_STATUS.path(id))
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
                            .uri(Endpoints.GET_ORDERS.value)
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
                            .uri("${Endpoints.GET_ORDERS.value}?sort=total:ASC&size=1${cursor?.let { c -> "&cursor=$c" } ?: ""}")
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
