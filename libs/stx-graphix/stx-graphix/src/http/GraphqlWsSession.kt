package com.softistx.graphix.http

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixException
import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.isSubscription
import com.softistx.graphix.subscribe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One graphql-ws socket. The HTTP layer owns the WebSocket; this owns the protocol.
 *
 * [send] is one already-encoded JSON text frame. [close] is the WebSocket close (code, reason).
 * Cancelling [scope] or calling [shutdown] drops every in-flight operation.
 *
 * [locale] is the socket's, not the operation's: a WebSocket negotiates `Accept-Language` once, at
 * the handshake, and every operation on it is answered in that language.
 */
class GraphqlWsSession(
    private val engine: Graphix,
    private val json: Json,
    private val send: suspend (String) -> Unit,
    private val close: suspend (code: Int, reason: String) -> Unit,
    private val scope: CoroutineScope,
    initTimeout: Duration = 3.seconds,
    private val locale: Locale? = null,
) {
    private val mutex = Mutex()
    private val sendMutex = Mutex()
    private var acked = false
    private var closed = false
    private val active = mutableMapOf<String, Job>()
    private val initJob: Job? =
        initTimeout.takeIf { it.isPositive() && it.isFinite() }?.let { timeout ->
            scope.launch {
                delay(timeout)
                mutex.withLock {
                    if (!acked) shut(GraphqlWsClose.INIT_TIMEOUT, "Connection initialisation timeout")
                }
            }
        }

    /** One text frame from the client. Invalid JSON or type closes the socket. */
    suspend fun incoming(text: String) {
        val frame =
            try {
                json.decodeFromString(GraphqlWsFrame.serializer(), text)
            } catch (_: SerializationException) {
                mutex.withLock { shut(GraphqlWsClose.INVALID, "Invalid message") }
                return
            }
        when (frame.type) {
            "connection_init" -> init()
            "ping" -> emit("pong", payload = frame.payload)
            "pong" -> Unit
            "subscribe" -> startOperation(frame)
            "complete" -> complete(frame.id)
            else -> mutex.withLock { shut(GraphqlWsClose.INVALID, "Invalid message type: ${frame.type}") }
        }
    }

    /** Cancels every operation. The HTTP layer calls this when the socket goes away. */
    fun shutdown() {
        initJob?.cancel()
        active.values.forEach { it.cancel() }
        active.clear()
    }

    private suspend fun init() {
        mutex.withLock {
            if (acked) {
                shut(GraphqlWsClose.TOO_MANY_INITS, "Too many initialisation requests")
                return
            }
            acked = true
            initJob?.cancel()
        }
        emit("connection_ack")
    }

    private suspend fun startOperation(frame: GraphqlWsFrame) {
        val id = frame.id
        if (id.isNullOrBlank()) {
            mutex.withLock { shut(GraphqlWsClose.INVALID, "subscribe needs an id") }
            return
        }
        val request =
            try {
                val payload = frame.payload ?: throw BadGraphixHttp("subscribe needs a payload")
                json.decodeFromJsonElement(GraphixHttpRequest.serializer(), payload).toGraphixRequest(locale)
            } catch (failure: Exception) {
                mutex.withLock { shut(GraphqlWsClose.INVALID, failure.message ?: "invalid subscribe") }
                return
            }
        mutex.withLock {
            if (closed) return
            if (!acked) {
                shut(GraphqlWsClose.UNAUTHORIZED, "Unauthorized")
                return
            }
            if (id in active) {
                shut(GraphqlWsClose.SUBSCRIBER_EXISTS, "Subscriber for $id already exists")
                return
            }
            active[id] = scope.launch { run(id, request) }
        }
    }

    private suspend fun run(
        id: String,
        request: GraphixRequest,
    ) {
        try {
            if (request.isSubscription()) {
                engine.subscribe(request).collect { result -> emit("next", id, nextPayload(result.toHttp())) }
            } else {
                emit("next", id, nextPayload(engine.execute(request).toHttp()))
            }
            emit("complete", id)
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (failure: GraphixException) {
            emit("error", id, errorPayload(failure.message ?: "GraphQL error"))
        } catch (failure: Exception) {
            emit("error", id, errorPayload(failure.message ?: failure::class.simpleName ?: "error"))
        } finally {
            mutex.withLock { active.remove(id) }
        }
    }

    private suspend fun complete(id: String?) {
        if (id == null) return
        mutex.withLock { active.remove(id)?.cancel() }
    }

    private suspend fun emit(
        type: String,
        id: String? = null,
        payload: JsonElement? = null,
    ) {
        if (closed) return
        val body =
            buildMap {
                put("type", JsonPrimitive(type))
                if (id != null) put("id", JsonPrimitive(id))
                if (payload != null) put("payload", payload)
            }
        sendMutex.withLock { send(json.encodeToString(JsonObject.serializer(), JsonObject(body))) }
    }

    private suspend fun shut(
        code: Int,
        reason: String,
    ) {
        if (closed) return
        closed = true
        shutdown()
        close(code, reason)
    }

    private fun nextPayload(http: GraphixHttpResponse): JsonElement = json.encodeToJsonElement(GraphixHttpResponse.serializer(), http)

    private fun errorPayload(message: String): JsonArray = JsonArray(listOf(JsonObject(mapOf("message" to JsonPrimitive(message)))))
}
