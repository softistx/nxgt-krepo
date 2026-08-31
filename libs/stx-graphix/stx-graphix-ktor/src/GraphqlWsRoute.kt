package com.strange.graphix.ktor

import com.strange.graphix.Graphix
import com.strange.graphix.http.GraphqlWsSession
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json

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
        )
    try {
        for (frame in incoming) {
            if (frame is Frame.Text) session.incoming(frame.readText())
        }
    } finally {
        session.shutdown()
    }
}
