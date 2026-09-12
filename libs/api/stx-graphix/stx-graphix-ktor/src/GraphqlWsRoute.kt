package com.softistx.graphix.ktor

import com.softistx.graphix.Graphix
import com.softistx.graphix.http.GraphqlWsSession
import com.softistx.graphix.http.acceptedLocale
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

internal suspend fun DefaultWebSocketServerSession.handleGraphqlWs(
    engine: Graphix,
    json: Json,
) {
    val session =
        GraphqlWsSession(
            engine = engine,
            json = json,
            send = { text -> send(Frame.Text(text)) },
            close = { code, reason -> this.close(CloseReason(code.toShort(), reason)) },
            scope = this,
            // The handshake is the only request a socket has, so its Accept-Language is the
            // language every operation on this socket is answered in.
            locale = acceptedLocale(call.request.headers[HttpHeaders.AcceptLanguage]),
            // …and by the same token it is the only ApplicationCall there is. It carries the
            // handshake's headers and its authentication; anything that changes mid-socket comes
            // from the client's `connection_init` payload instead, which the session reads per
            // operation.
            context = mapOf<KClass<*>, Any>(ApplicationCall::class to call),
        )
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) session.incoming(frame.readText())
        }
    } finally {
        session.shutdown()
    }
}
