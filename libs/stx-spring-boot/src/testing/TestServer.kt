package com.strange.spring.testing

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Where the application under test answers.
 *
 * **This exists so an application stops keeping a `BASE_URL` constant in step with its
 * `application-test.yaml`.** Every spec module used to declare the port twice and had no way to
 * notice when the two drifted apart; here the default is the convention and the running server
 * corrects it.
 *
 * The default is read before any context exists, which is what a `@SpringBootTest(DEFINED_PORT)` spec
 * needs: its body runs at construction, and for a spec with no constructor parameters that is before
 * Spring has started anything. [TestServerConfiguration] then overwrites it with the port the server
 * actually took, so re-annotating a subclass `RANDOM_PORT` works too — a spec that asks for a bean in
 * its constructor has a started context by the time its body runs.
 */
object TestServer {
    /** `server.port` under the `test` profile, until a started server says otherwise. */
    @Volatile
    var port: Int = STX_TEST_PORT

    /** Where the application answers. */
    val baseUrl: String get() = "http://localhost:$port"

    /**
     * The port this repo's `test` profile binds.
     *
     * Not 8080: a demo left running in another terminal should not make a suite fail — or worse, pass
     * against the wrong process.
     */
    const val STX_TEST_PORT = 8088
}

/** Records the port the server actually took. Imported by [SpringSpec] and [MongoSpec]. */
@TestConfiguration(proxyBeanMethods = false)
class TestServerConfiguration {
    @Bean
    fun testServerPort(): ApplicationListener<WebServerInitializedEvent> = ApplicationListener { TestServer.port = it.webServer.port }
}

/**
 * A client for what a typed client cannot say: the envelope's JSON shape, a status no document
 * declared, the same message in two languages.
 *
 * **`bindToServer`, not `bindToApplicationContext`.** The kotlinx codecs, the exception advice, the
 * locale negotiation and the `Instant` converters are all auto-configurations, and a client bound to
 * a context bypasses several of them — so it would assert against a stack no caller ever meets.
 */
fun webTestClient(baseUrl: String = TestServer.baseUrl): WebTestClient = WebTestClient.bindToServer().baseUrl(baseUrl).build()
