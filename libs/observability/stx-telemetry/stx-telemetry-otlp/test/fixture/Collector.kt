package com.softistx.telemetry.otlp.fixture

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream

/** One request as it arrived. */
data class Received(
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

/**
 * A collector, in the JDK.
 *
 * `com.sun.net.httpserver.HttpServer` ships with the JVM, starts in a millisecond and answers
 * exactly what a spec tells it to — which is what these specs need and a container is not. The one
 * spec that wants a *real* collector is gated on `OTLP_TEST_ENDPOINT` and lives next door.
 */
class FakeCollector(
    private val answers: (Int) -> Answer = { Answer(200, """{"partialSuccess":{}}""") },
) : AutoCloseable {
    private val received = ConcurrentLinkedQueue<Received>()
    private var calls = 0

    private val server: HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> handle(exchange) }
            executor = null
            start()
        }

    val endpoint: String get() = "http://127.0.0.1:${server.address.port}"

    val requests: List<Received> get() = received.toList()

    fun on(path: String): Received? = requests.firstOrNull { it.path == path }

    private fun handle(exchange: HttpExchange) {
        val gzipped = exchange.requestHeaders.getFirst("Content-Encoding") == "gzip"
        val body =
            exchange.requestBody.use { stream ->
                if (gzipped) GZIPInputStream(stream).readBytes() else stream.readBytes()
            }
        received +=
            Received(
                path = exchange.requestURI.path,
                headers = exchange.requestHeaders.entries.associate { it.key to it.value.first() },
                body = String(body),
            )

        val answer = answers(calls++)
        val payload = answer.body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(answer.status, payload.size.toLong())
        exchange.responseBody.use { it.write(payload) }
    }

    override fun close() = server.stop(0)
}

data class Answer(
    val status: Int,
    val body: String = "",
)
