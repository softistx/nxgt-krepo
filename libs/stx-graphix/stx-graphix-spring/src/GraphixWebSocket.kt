package com.softistx.graphix.spring

import com.softistx.graphix.Graphix
import com.softistx.graphix.http.GRAPHQL_TRANSPORT_WS
import com.softistx.graphix.http.GraphqlWsSession
import com.softistx.graphix.http.acceptedLocale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import kotlin.reflect.KClass

/** graphql-ws on the GraphQL path. Only registered when `stx.graphix.subscriptions=graphql-ws`. */
class GraphixWebSocketHandler(
    private val engine: Graphix,
    private val json: Json,
    private val context: Map<KClass<*>, Any> = emptyMap(),
) : WebSocketHandler {
    /**
     * The same handler bound to one handshake, so every operation on that socket carries its
     * [ServerWebExchange] the way an HTTP operation carries its own.
     *
     * `WebSocketHandler.handle` is given only the session, and a `WebSocketSession` has no exchange
     * — [GraphixUpgradeMapping] is the one place that still holds it, so the binding happens there.
     */
    internal fun boundTo(exchange: ServerWebExchange): GraphixWebSocketHandler =
        GraphixWebSocketHandler(engine, json, mapOf(ServerWebExchange::class to exchange))

    override fun getSubProtocols(): List<String> = listOf(GRAPHQL_TRANSPORT_WS)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val sink = Sinks.many().unicast().onBackpressureBuffer<WebSocketMessage>()
        val incoming =
            mono {
                coroutineScope {
                    val ws =
                        GraphqlWsSession(
                            engine = engine,
                            json = json,
                            send = { text -> sink.emitNext(session.textMessage(text), Sinks.EmitFailureHandler.FAIL_FAST) },
                            close = { code, reason -> session.close(CloseStatus(code, reason)).awaitSingleOrNull() },
                            scope = this,
                            // A socket negotiates its language once, at the handshake.
                            locale = acceptedLocale(session.handshakeInfo.headers.getFirst(HttpHeaders.ACCEPT_LANGUAGE)),
                            // …and by the same token has one exchange, the handshake's. Anything
                            // that changes mid-socket comes from the client's `connection_init`
                            // payload instead, which the session reads per operation.
                            context = context,
                        )
                    try {
                        session.receive().asFlow().collect { message ->
                            if (message.type == WebSocketMessage.Type.TEXT) ws.incoming(message.payloadAsText)
                        }
                    } finally {
                        ws.shutdown()
                        sink.tryEmitComplete()
                    }
                }
            }
        return session.send(sink.asFlux()).and(incoming)
    }
}

/** Matches the GraphQL path only on `Upgrade: websocket`, so POST/GET still hit the router. */
class GraphixUpgradeMapping(
    path: String,
    handler: WebSocketHandler,
) : SimpleUrlHandlerMapping() {
    init {
        urlMap = mapOf(path to handler)
        order = -1
    }

    override fun getHandlerInternal(exchange: ServerWebExchange): Mono<Any> {
        if (!"websocket".equals(exchange.request.headers.getFirst(HttpHeaders.UPGRADE), ignoreCase = true)) {
            return Mono.empty()
        }
        // The last point that still has the exchange: the adapter hands the handler a session only.
        return super.getHandlerInternal(exchange).map { handler ->
            if (handler is GraphixWebSocketHandler) handler.boundTo(exchange) else handler
        }
    }
}
