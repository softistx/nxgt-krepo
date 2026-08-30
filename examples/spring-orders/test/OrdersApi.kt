package com.strange.example.orders

import com.strange.example.orders.api.utils.apiErrorFilter
import com.strange.example.orders.api.utils.apiOperationProcessor
import com.strange.example.orders.api.utils.registerApiEnumConverters
import com.strange.spring.client.httpServiceFactory
import com.strange.testing.containers.mongoContainer
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.Json
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.reactive.context.ReactiveWebServerApplicationContext
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * The MongoDB the specs here run against.
 *
 * `MONGO_TEST_URI` reuses a server that is already up; otherwise `stx-testing` starts a `mongo:8`
 * container for the run and stops it afterwards. With neither, `available` is false and every
 * feature below is `.config(enabled = mongo.available)`, so the specs report skipped rather than
 * failing on a machine with no Docker.
 */
val mongo = mongoContainer()

private val databases = AtomicInteger()

/**
 * The application on a real port, and the generated interfaces pointed at it.
 *
 * **This is what an end-to-end test is here: the built application over HTTP, driven through the
 * same interfaces its controllers implement.** `OrderController` implements `IOrdersService`; so
 * does the client a spec calls; neither of them wrote it, and a change to `openapi/` that only one
 * side followed stops compiling on both at once. A hand-written client, or a mock of the service,
 * would prove neither.
 *
 * One instance per spec, each with a database of its own — `MONGO_TEST_URI` usually names the
 * workspace's own replica set, and a run that reuses a server has to leave it as it found it, and
 * must not be able to see what another spec wrote.
 */
class OrdersApi {
    private var context: ReactiveWebServerApplicationContext? = null

    lateinit var baseUrl: String
        private set

    lateinit var template: ReactiveMongoTemplate
        private set

    /** For what a typed client hides: the envelope's JSON shape, a status the document never declared. */
    lateinit var web: WebTestClient
        private set

    /** Built once; every tag's interface comes off it with `withClient`. */
    lateinit var factory: HttpServiceProxyFactory
        private set

    /**
     * Boots the application and points the clients at it.
     *
     * [headers] runs per request, which is where an API with authentication puts its token —
     * `start { it.setBearerAuth(token) }`, the seam `nxgt-rest`'s specs use. This one authenticates
     * nobody, so it is empty here and present anyway, because it is the parameter a real API's spec
     * reaches for first.
     */
    fun start(headers: (HttpHeaders) -> Unit = {}) {
        val database = "spring_orders_test_${databases.incrementAndGet()}"
        val started =
            SpringApplicationBuilder(OrdersApplication::class.java)
                .web(WebApplicationType.REACTIVE)
                // Arguments and not `.properties()`: that method contributes Boot's *default*
                // property source, the lowest-precedence one there is, so `resources/application.yaml`
                // won every key it also names — including `spring.data.mongodb.uri`.
                .run(
                    "--server.port=0",
                    "--spring.data.mongodb.uri=${mongo.endpoint.withDatabase(database)}",
                    // A test suite should not pass over a translation nobody wrote.
                    "--stx.i18n.fail-on-missing-key=true",
                ) as ReactiveWebServerApplicationContext

        context = started
        template = started.getBean(ReactiveMongoTemplate::class.java)
        baseUrl = "http://localhost:${started.webServer!!.port}"
        web = WebTestClient.bindToServer().baseUrl(baseUrl).build()
        factory = apiFactory(baseUrl, started.getBean("stxWebJson", Json::class.java), headers)
    }

    /**
     * Suspends until the migrations have run.
     *
     * `MigrationRunner` listens for `ApplicationReadyEvent` and suspends, and Spring does not wait
     * for a suspending listener — `run` returns while the seed is still being written. So the port
     * is open before the data is there, and a spec that assumed otherwise would fail on whichever
     * scenario happened to go first. A migration is not a startup gate.
     */
    suspend fun ready() {
        eventually(10.seconds) {
            template.count(Query(), "migrations").awaitSingle() shouldBe 2L
        }
    }

    /** Drops the spec's database and closes the context, whether or not its scenarios passed. */
    suspend fun stop() {
        context?.let { started ->
            runCatching {
                started
                    .getBean(ReactiveMongoTemplate::class.java)
                    .mongoDatabase
                    .awaitSingle()
                    .drop()
                    .awaitFirstOrNull()
            }
            started.close()
        }
        context = null
    }
}

/**
 * The proxy factory the generated clients need, from `stx-spring-boot`'s own [httpServiceFactory].
 *
 * The client extensions are the library's, not this module's: a spec that assembled its own
 * `WebClient` would be asserting against a transport no caller uses. What is added here is only what
 * a *generated* client needs on top, and none of the three fails at build time:
 *
 * - **The `WebClient` decodes with kotlinx, not Jackson.** `models: Kotlinx` generates
 *   `@Serializable` classes whose `placedAt` is a `kotlin.time.Instant`, a type Jackson has never
 *   heard of. The application's own `stxWebJson` is reused rather than a fresh `Json`, so the client
 *   reads exactly what the server wrote.
 * - **The conversion service knows the generated enums.** Spring writes an enum argument with
 *   `Enum.name()` and never consults `toString()`, so a document spelling a value in kebab-case
 *   would go out as the Kotlin name — a request that succeeds and matches nothing.
 * - **`apiOperationProcessor` carries the operation across.** By the time a `ClientRequest` exists
 *   the method is gone, so without it `apiErrorFilter` cannot tell which operation failed and every
 *   typed failure falls back to the untyped `ApiException`.
 */
fun apiFactory(
    baseUrl: String,
    json: Json,
    headers: (HttpHeaders) -> Unit = {},
): HttpServiceProxyFactory =
    httpServiceFactory(
        baseUrl,
        headers,
        factory = {
            conversionService(DefaultFormattingConversionService().also(::registerApiEnumConverters))
            httpRequestValuesProcessor(apiOperationProcessor())
        },
    ) {
        codecs {
            it.defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
            it.defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
        }
        // Turns a documented non-2xx into the exception the document describes. It runs closer to
        // the transport than `httpServiceFactory`'s own status handler, so what a caller catches is
        // the generated `ErrorResponseException` carrying a parsed body rather than the library's
        // untyped `ApiException` — which is what an external consumer of this API would get.
        filter(apiErrorFilter())
    }

/**
 * [this] with its database replaced by [name].
 *
 * **Not `spring.data.mongodb.database`.** Boot reads that property only when it is building a
 * connection string from `host`/`port`; once `spring.data.mongodb.uri` is set the database comes
 * from the URI and the property is ignored, silently. `MongoDBContainer` hands back a URL ending in
 * `/test` and a `MONGO_TEST_URI` naming the workspace's replica set usually ends in no database at
 * all — so both spellings were landing every run in one shared database, and the isolation these
 * specs claim was not happening. What surfaced it was a manual `./kotlin run` against the same
 * server leaving rows the paging scenario then counted.
 */
private fun String?.withDatabase(name: String): String {
    // Non-null by the time this is called: a spec starts nothing unless the service resolved.
    val uri = requireNotNull(this) { "no MongoDB endpoint" }
    val query = uri.substringAfter("?", "").let { if (it.isEmpty()) "" else "?$it" }
    val base = uri.substringBefore("?").trimEnd('/')
    // "mongodb://host:port" has two slashes; a third one starts the database path.
    val host = if (base.count { it == '/' } > 2) base.substringBeforeLast('/') else base
    return "$host/$name$query"
}
