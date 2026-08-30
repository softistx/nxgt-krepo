package com.strange.graphix.spring

import com.strange.graphix.Graphix
import com.strange.graphix.http.*
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Mono

/** WebFlux adapter: the same JSON envelope as Ktor, over `RouterFunction`. */
internal class GraphixHandler(
    private val engine: Graphix,
    private val json: Json,
    private val path: String,
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
            mono { handlePost(body) }
        }

    private fun get(request: ServerRequest): Mono<ServerResponse> = mono { handleGet(request) }

    private suspend fun handlePost(body: String): ServerResponse {
        val graphixRequest =
            try {
                json.decodeFromString(GraphixHttpRequest.serializer(), body).toGraphixRequest()
            } catch (failure: SerializationException) {
                return badRequest("malformed GraphQL JSON: ${failure.message}")
            } catch (failure: BadGraphixHttp) {
                return badRequest(failure.message ?: "malformed GraphQL request")
            }
        return ok(engine.execute(graphixRequest).toHttp())
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
            ).toGraphixRequest()
        return ok(engine.execute(graphixRequest).toHttp())
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
