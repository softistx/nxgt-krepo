package com.strange.graphql.ktor

import com.strange.graphql.GraphQl
import com.strange.graphql.GraphQlRequest
import com.strange.graphql.http.BadGraphQlHttp
import com.strange.graphql.http.GraphQlHttpError
import com.strange.graphql.http.GraphQlHttpRequest
import com.strange.graphql.http.GraphQlHttpResponse
import com.strange.graphql.http.toGraphQlRequest
import com.strange.graphql.http.toHttp
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal fun Route.graphqlRoute(
    path: String,
    engine: GraphQl,
    json: Json,
) {
    route(path) {
        post { call.handlePost(engine, json) }
        get { call.handleGet(engine, json) }
    }
}

private suspend fun ApplicationCall.handlePost(
    engine: GraphQl,
    json: Json,
) {
    val body = receiveText()
    val request =
        try {
            json.decodeFromString(GraphQlHttpRequest.serializer(), body).toGraphQlRequest()
        } catch (failure: SerializationException) {
            return respondBadRequest(json, "malformed GraphQL JSON: ${failure.message}")
        } catch (failure: BadGraphQlHttp) {
            return respondBadRequest(json, failure.message ?: "malformed GraphQL request")
        }
    respondResult(engine, json, request)
}

private suspend fun ApplicationCall.handleGet(
    engine: GraphQl,
    json: Json,
) {
    val query = request.queryParameters["query"]
    if (query.isNullOrBlank()) {
        return respondBadRequest(json, "a GraphQL GET needs a query parameter")
    }
    val variables =
        request.queryParameters["variables"]?.let { raw ->
            try {
                json.decodeFromString(JsonObject.serializer(), raw)
            } catch (failure: SerializationException) {
                return respondBadRequest(json, "malformed GraphQL variables: ${failure.message}")
            }
        }
    val request =
        GraphQlHttpRequest(
            query = query,
            operationName = request.queryParameters["operationName"],
            variables = variables,
        ).toGraphQlRequest()
    respondResult(engine, json, request)
}

private suspend fun ApplicationCall.respondResult(
    engine: GraphQl,
    json: Json,
    request: GraphQlRequest,
) {
    val result = engine.execute(request).toHttp()
    respondText(
        json.encodeToString(GraphQlHttpResponse.serializer(), result),
        ContentType.Application.Json,
        HttpStatusCode.OK,
    )
}

private suspend fun ApplicationCall.respondBadRequest(
    json: Json,
    message: String,
) {
    val body = GraphQlHttpResponse(errors = listOf(GraphQlHttpError(message)))
    respondText(
        json.encodeToString(GraphQlHttpResponse.serializer(), body),
        ContentType.Application.Json,
        HttpStatusCode.BadRequest,
    )
}
