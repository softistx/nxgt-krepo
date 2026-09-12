package com.softistx.graphix.spring

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.http.BadGraphixHttp
import com.softistx.graphix.http.GraphixHttpError
import com.softistx.graphix.http.GraphixHttpRequest
import com.softistx.graphix.http.GraphixHttpResponse
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.http.acceptedLocale
import com.softistx.graphix.http.toGraphixRequest
import com.softistx.graphix.http.toHttp
import com.softistx.graphix.isSubscription
import com.softistx.graphix.subscribe
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.Locale
import kotlin.reflect.KClass

/** WebFlux adapter: the same JSON envelope as Ktor, over `RouterFunction`. */
internal class GraphixHandler(
    private val engine: Graphix,
    private val json: Json,
    private val path: String,
    private val subscriptions: SubscriptionProtocol = SubscriptionProtocol.Sse,
) {
    /** POST and GET at [path]. Field errors stay HTTP 200; malformed JSON is 400. */
    fun router(): RouterFunction<ServerResponse> =
        RouterFunctions
            .route()
            .POST(path, this::post)
            .GET(path, this::get)
            .build()

    private fun post(request: ServerRequest): Mono<ServerResponse> =
        request.bodyToMono<String>().defaultIfEmpty("").flatMap { body ->
            mono { handlePost(body, request.preferredLocale(), request.context()) }
        }

    private fun get(request: ServerRequest): Mono<ServerResponse> = mono { handleGet(request) }

    private suspend fun handlePost(
        body: String,
        locale: Locale?,
        context: Map<KClass<*>, Any>,
    ): ServerResponse {
        val graphixRequest =
            try {
                json.decodeFromString(GraphixHttpRequest.serializer(), body).toGraphixRequest(locale)
            } catch (failure: SerializationException) {
                return badRequest("malformed GraphQL JSON: ${failure.message}")
            } catch (failure: BadGraphixHttp) {
                return badRequest(failure.message ?: "malformed GraphQL request")
            }
        return respond(graphixRequest, context)
    }

    private suspend fun handleGet(request: ServerRequest): ServerResponse {
        val query = request.queryParam("query").orElse(null)
        if (query.isNullOrBlank()) {
            return badRequest("a GraphQL GET needs a query parameter")
        }
        val variables =
            request.queryParam("variables").orElse(null)?.let { raw ->
                try {
                    json.decodeFromString(JsonObject.serializer(), raw)
                } catch (failure: SerializationException) {
                    return badRequest("malformed GraphQL variables: ${failure.message}")
                }
            }
        val graphixRequest =
            GraphixHttpRequest(
                query = query,
                operationName = request.queryParam("operationName").orElse(null),
                variables = variables,
            ).toGraphixRequest(request.preferredLocale())
        return respond(graphixRequest, request.context())
    }

    /** The request's `Accept-Language`, as the locale its coercion errors are translated in. */
    private fun ServerRequest.preferredLocale(): Locale? = acceptedLocale(headers().firstHeader(HttpHeaders.ACCEPT_LANGUAGE))

    /**
     * The exchange *is* the operation's context. Taking it off the [ServerRequest] rather than out
     * of a reactor context sidesteps the `mono { }` bridge entirely — nothing here depends on a
     * coroutine context surviving it.
     */
    private fun ServerRequest.context(): Map<KClass<*>, Any> = mapOf(ServerWebExchange::class to exchange())

    private suspend fun respond(
        request: GraphixRequest,
        context: Map<KClass<*>, Any>,
    ): ServerResponse {
        if (request.isSubscription()) {
            if (subscriptions == SubscriptionProtocol.GraphqlWs) {
                return badRequest("subscriptions use graphql-ws")
            }
            val events =
                engine.subscribe(request, context).map { json.encodeToString(GraphixHttpResponse.serializer(), it.toHttp()) }
            return ServerResponse
                .ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .bodyAndAwait(events)
        }
        return ok(engine.execute(request, context).toHttp())
    }

    private suspend fun ok(body: GraphixHttpResponse): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(json.encodeToString(GraphixHttpResponse.serializer(), body))

    private suspend fun badRequest(message: String): ServerResponse =
        ServerResponse
            .badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(
                json.encodeToString(
                    GraphixHttpResponse.serializer(),
                    GraphixHttpResponse(errors = listOf(GraphixHttpError(message))),
                ),
            )
}
