package com.softistx.graphix.http

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** WebSocket sub-protocol for graphql-ws (`Sec-WebSocket-Protocol`). */
const val GRAPHQL_TRANSPORT_WS = "graphql-transport-ws"

/** Close codes from the graphql-ws protocol. */
object GraphqlWsClose {
    const val INVALID = 4400
    const val UNAUTHORIZED = 4401
    const val INIT_TIMEOUT = 4408
    const val SUBSCRIBER_EXISTS = 4409
    const val TOO_MANY_INITS = 4429
}

/**
 * What the client sent with `connection_init`, in the operation context of every operation on that
 * socket. `null` when it sent none.
 *
 * A socket carries no `Authorization` header past the handshake, so this is where a graphql-ws
 * client puts a credential — by convention `{"authToken": "…"}`, but the protocol says only that it
 * is arbitrary JSON, which is why this hands over the element rather than a shape of its own. A
 * `GraphixInterceptor` reads it and decides what the operation may do.
 */
data class GraphqlWsInit(
    val payload: JsonElement?,
)

/** One graphql-ws JSON frame. Null [id] / [payload] are omitted on the wire by the session. */
@Serializable
internal data class GraphqlWsFrame(
    val type: String,
    val id: String? = null,
    val payload: JsonElement? = null,
)
