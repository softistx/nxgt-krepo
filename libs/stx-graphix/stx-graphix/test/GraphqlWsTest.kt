package com.strange.graphix

import com.strange.common.serialization.lenientJson
import com.strange.graphix.fixture.GreetingQueries
import com.strange.graphix.fixture.HangSubscriptions
import com.strange.graphix.fixture.TickSubscriptions
import com.strange.graphix.http.GRAPHQL_TRANSPORT_WS
import com.strange.graphix.http.GraphqlWsClose
import com.strange.graphix.http.GraphqlWsSession
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class GraphqlWsTest :
    FeatureSpec({
        feature("protocol") {
            scenario("connection_init is acknowledged") {
                session { ws, out, _ ->
                    ws.incoming("""{"type":"connection_init"}""")
                    out.receive() shouldContain "connection_ack"
                }
            }

            scenario("subscribe before ack closes unauthorized") {
                session { ws, _, closed ->
                    ws.incoming("""{"id":"1","type":"subscribe","payload":{"query":"subscription { ticks }"}}""")
                    closed.get().shouldNotBeNull().first shouldBe GraphqlWsClose.UNAUTHORIZED
                }
            }

            scenario("a subscription emits next then complete") {
                session { ws, out, _ ->
                    ws.incoming("""{"type":"connection_init"}""")
                    out.receive()
                    ws.incoming("""{"id":"1","type":"subscribe","payload":{"query":"subscription { ticks }"}}""")
                    val events = List(4) { out.receive() }
                    events[0] shouldContain """"type":"next""""
                    events[0] shouldContain """"ticks":1"""
                    events[2] shouldContain """"ticks":3"""
                    events[3] shouldContain """"type":"complete""""
                }
            }

            scenario("a query over the socket is one next then complete") {
                session { ws, out, _ ->
                    ws.incoming("""{"type":"connection_init"}""")
                    out.receive()
                    ws.incoming("""{"id":"q","type":"subscribe","payload":{"query":"{ hello }"}}""")
                    out.receive() shouldContain """"hello":"world""""
                    out.receive() shouldContain "complete"
                }
            }

            scenario("ping is answered with pong") {
                session { ws, out, _ ->
                    ws.incoming("""{"type":"ping"}""")
                    out.receive() shouldBe """{"type":"pong"}"""
                }
            }

            scenario("a second connection_init closes the socket") {
                session { ws, out, closed ->
                    ws.incoming("""{"type":"connection_init"}""")
                    out.receive()
                    ws.incoming("""{"type":"connection_init"}""")
                    closed.get().shouldNotBeNull().first shouldBe GraphqlWsClose.TOO_MANY_INITS
                }
            }

            scenario("duplicate subscribe ids close the socket") {
                session { ws, out, closed ->
                    ws.incoming("""{"type":"connection_init"}""")
                    out.receive()
                    ws.incoming("""{"id":"1","type":"subscribe","payload":{"query":"subscription { hang }"}}""")
                    ws.incoming("""{"id":"1","type":"subscribe","payload":{"query":"subscription { hang }"}}""")
                    closed.get().shouldNotBeNull().first shouldBe GraphqlWsClose.SUBSCRIBER_EXISTS
                }
            }

            scenario("init timeout closes when the client never acks") {
                session(initTimeout = 20.milliseconds) { _, _, closed ->
                    delay(80)
                    closed.get().shouldNotBeNull().first shouldBe GraphqlWsClose.INIT_TIMEOUT
                }
            }

            scenario("the sub-protocol name is graphql-transport-ws") {
                GRAPHQL_TRANSPORT_WS shouldBe "graphql-transport-ws"
            }
        }
    })

private class Closed {
    @Volatile
    var value: Pair<Int, String>? = null

    fun get(): Pair<Int, String>? = value
}

private suspend fun session(
    initTimeout: Duration = Duration.INFINITE,
    block: suspend (GraphqlWsSession, Channel<String>, Closed) -> Unit,
) {
    val engine =
        Graphix {
            query(GreetingQueries())
            subscription(TickSubscriptions())
            subscription(HangSubscriptions())
        }
    coroutineScope {
        val outgoing = Channel<String>(Channel.BUFFERED)
        val closed = Closed()
        val ws =
            GraphqlWsSession(
                engine = engine,
                json = lenientJson,
                send = { outgoing.send(it) },
                close = { code, reason -> closed.value = code to reason },
                scope = this,
                initTimeout = initTimeout,
            )
        try {
            withTimeout(2_000) { block(ws, outgoing, closed) }
        } finally {
            ws.shutdown()
            outgoing.close()
        }
    }
}
