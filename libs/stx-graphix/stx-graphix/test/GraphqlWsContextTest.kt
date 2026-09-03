package com.softistx.graphix

import com.softistx.common.serialization.lenientJson
import com.softistx.graphix.fixture.FakeCall
import com.softistx.graphix.fixture.SocketQueries
import com.softistx.graphix.http.GraphqlWsSession
import com.softistx.graphix.schema.contextParameter
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * What a graphql-ws operation can see: the handshake's own context, which the HTTP layer hands the
 * session once, and the `connection_init` payload, which the client sends after it.
 *
 * The payload matters because a socket has no `Authorization` header past the handshake, so the
 * protocol's own frame is the only place a credential can arrive — and it is read per operation,
 * not per socket.
 */
class GraphqlWsContextTest :
    FeatureSpec({

        feature("the connection_init payload") {
            scenario("reaches an operation as GraphqlWsInit") {
                socket { ws, out ->
                    ws.incoming("""{"type":"connection_init","payload":{"authToken":"t-42"}}""")
                    out.receive() shouldContain "connection_ack"
                    ws.incoming("""{"id":"1","type":"subscribe","payload":{"query":"{ token }"}}""")
                    out.receive() shouldContain """"token":"t-42""""
                }
            }

            scenario("is absent when the client sent none") {
                socket { ws, out ->
                    ws.incoming("""{"type":"connection_init"}""")
                    out.receive() shouldContain "connection_ack"
                    ws.incoming("""{"id":"1","type":"subscribe","payload":{"query":"{ token }"}}""")
                    out.receive() shouldContain """"token":"none""""
                }
            }
        }

        feature("the socket's context") {
            scenario("is on every operation, so the handshake call is a resolver parameter") {
                socket(mapOf(FakeCall::class to FakeCall(mapOf("X-User" to "ada")))) { ws, out ->
                    ws.incoming("""{"type":"connection_init"}""")
                    out.receive() shouldContain "connection_ack"
                    ws.incoming("""{"id":"1","type":"subscribe","payload":{"query":"{ handshake }"}}""")
                    out.receive() shouldContain """"handshake":"ada""""
                }
            }
        }
    })

private suspend fun socket(
    context: Map<KClass<*>, Any> = emptyMap(),
    block: suspend (GraphqlWsSession, Channel<String>) -> Unit,
) {
    val engine =
        Graphix {
            resolvers(SocketQueries())
            contextParameter(FakeCall::class)
        }
    coroutineScope {
        val outgoing = Channel<String>(Channel.BUFFERED)
        val ws =
            GraphqlWsSession(
                engine = engine,
                json = lenientJson,
                send = { outgoing.send(it) },
                close = { _, _ -> },
                scope = this,
                initTimeout = Duration.INFINITE,
                context = context,
            )
        try {
            withTimeout(2_000) { block(ws, outgoing) }
        } finally {
            ws.shutdown()
            outgoing.close()
        }
    }
}
