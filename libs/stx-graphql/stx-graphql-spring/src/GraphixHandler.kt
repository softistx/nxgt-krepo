package com.strange.graphql.spring

import com.strange.graphql.Graphix
import com.strange.graphql.http.BadGraphixHttp
import com.strange.graphql.http.GraphixHttpError
import com.strange.graphql.http.GraphixHttpRequest
import com.strange.graphql.http.GraphixHttpResponse
import com.strange.graphql.http.toGraphixRequest
import com.strange.graphql.http.toHttp
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import reactor.core.publisher.Mono

internal class GraphixHandler(
    private val engine: Graphix,
    private val json: Json,
    private val path: String,
) {
    fun router(): RouterFunction<ServerResponse> =
        RouterFunctions
            .route()
            .POST(path, this::post)
            .GET(path, this::get)
            .build()

    private fun post(request: ServerRequest): Mono<ServerResponse> =
        request.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { body ->
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
