package com.strange.graphql.spring

import com.strange.graphql.GraphQl
import com.strange.graphql.http.BadGraphQlHttp
import com.strange.graphql.http.GraphQlHttpError
import com.strange.graphql.http.GraphQlHttpRequest
import com.strange.graphql.http.GraphQlHttpResponse
import com.strange.graphql.http.toGraphQlRequest
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

internal class GraphQlHandler(
    private val engine: GraphQl,
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
        val graphQlRequest =
            try {
                json.decodeFromString(GraphQlHttpRequest.serializer(), body).toGraphQlRequest()
            } catch (failure: SerializationException) {
                return badRequest("malformed GraphQL JSON: ${failure.message}")
            } catch (failure: BadGraphQlHttp) {
                return badRequest(failure.message ?: "malformed GraphQL request")
            }
        return ok(engine.execute(graphQlRequest).toHttp())
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
        val graphQlRequest =
            GraphQlHttpRequest(
                query = query,
                operationName = request.queryParam("operationName").orElse(null),
                variables = variables,
            ).toGraphQlRequest()
        return ok(engine.execute(graphQlRequest).toHttp())
    }

    private suspend fun ok(body: GraphQlHttpResponse): ServerResponse =
        ServerResponse
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(json.encodeToString(GraphQlHttpResponse.serializer(), body))

    private suspend fun badRequest(message: String): ServerResponse =
        ServerResponse
            .badRequest()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(
                json.encodeToString(
                    GraphQlHttpResponse.serializer(),
                    GraphQlHttpResponse(errors = listOf(GraphQlHttpError(message))),
                ),
            )
}
