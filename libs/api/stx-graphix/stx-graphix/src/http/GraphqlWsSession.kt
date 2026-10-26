package com.softistx.graphix.http

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixException
import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.isSubscription
import com.softistx.graphix.subscribe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One graphql-ws socket. The HTTP layer owns the WebSocket; this owns the protocol.
 *
 * [send] is one already-encoded JSON text frame. [close] is the WebSocket close (code, reason).
 * Cancelling [scope] or calling [shutdown] drops every in-flight operation.
 *
 * [locale] is the socket's, not the operation's: a WebSocket negotiates `Accept-Language` once, at
 * the handshake, and every operation on it is answered in that language. [context] is the socket's
 * too — the handshake call, which is the only request a socket has.
 *
 * **Interceptors still run per operation**, because every `subscribe` frame goes through
 * `Graphix.execute` / `Graphix.subscribe` on its own. That is the difference that matters for a
 * credential: the `connection_init` payload is carried in the context as [GraphqlWsInit], and an
 * interceptor reads it once per operation rather than once per socket, so a token that expires
 * mid-socket is seen to have expired.
 */
class GraphqlWsSession(
    private val engine: Graphix,
    private val json: Json,
    private val send: suspend (String) -> Unit,
    private val close: suspend (code: Int, reason: String) -> Unit,
    private val scope: CoroutineScope,
    initTimeout: Duration = 3.seconds,
    private val locale: Locale? = null,
    private val context: Map<KClass<*>, Any> = emptyMap(),
) {
    private val mutex = Mutex()
    private val sendMutex = Mutex()
    private var acked = false
    @Volatile
    private var closed = false
    private var init: GraphqlWsInit = GraphqlWsInit(null)
    // Concurrent: `shutdown()` is not suspending, so it cannot take the mutex (the HTTP layer calls it
    // from a close handler, and `shut` calls it already holding the lock).
    private val active = ConcurrentHashMap<String, Job>()
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
            "connection_init" -> init(frame.payload)
            "ping" -> emit("pong", payload = frame.payload)
            "pong" -> Unit
            "subscribe" -> startOperation(frame)
            "complete" -> complete(frame.id)
            else -> mutex.withLock { shut(GraphqlWsClose.INVALID, "Invalid message type: ${frame.type}") }
        }
    }

    /** Cancels every operation. The HTTP layer calls this when the socket goes away. */
    fun shutdown() {
        closed = true
        initJob?.cancel()
        // Remove-then-cancel per key, so an entry added meanwhile is either drained here or sees `closed`.
        active.keys.toList().forEach { active.remove(it)?.cancel() }
    }

    private suspend fun init(payload: JsonElement?) {
        mutex.withLock {
            if (acked) {
                shut(GraphqlWsClose.TOO_MANY_INITS, "Too many initialisation requests")
                return
            }
            // Kept rather than dropped: this is where a graphql-ws client sends its credential,
            // and a socket has no Authorization header of its own to send it in.
            init = GraphqlWsInit(payload)
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
            if (active.containsKey(id)) {
                shut(GraphqlWsClose.SUBSCRIBER_EXISTS, "Subscriber for $id already exists")
                return
            }
            // Read under the lock, with `init`, so the launched coroutine cannot race the ack.
            val operationContext = context + (GraphqlWsInit::class to init)
            // Lazy, so the entry is in the map before the operation can finish and remove it.
            val job = scope.launch(start = CoroutineStart.LAZY) { run(id, request, operationContext) }
            active[id] = job
            // A `shutdown()` that ran between the check above and the put has already drained.
            if (closed) {
                active.remove(id, job)
                job.cancel()
            } else {
                job.start()
            }
        }
    }

    private suspend fun run(
        id: String,
        request: GraphixRequest,
        context: Map<KClass<*>, Any>,
    ) {
        try {
            if (request.isSubscription()) {
                engine.subscribe(request, context).collect { result -> emit("next", id, nextPayload(result.toHttp())) }
            } else {
                emit("next", id, nextPayload(engine.execute(request, context).toHttp()))
            }
            emit("complete", id)
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (failure: GraphixException) {
            emit("error", id, errorPayload(failure.message ?: "GraphQL error"))
        } catch (failure: Exception) {
            emit("error", id, errorPayload(failure.message ?: failure::class.simpleName ?: "error"))
        } finally {
            // Only this operation's own entry: a `complete` followed by a new `subscribe` on the same
            // id has already put a different job under it.
            coroutineContext[Job]?.let { active.remove(id, it) }
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
