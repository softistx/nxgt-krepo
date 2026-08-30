package com.strange.graphix.ktor

import com.strange.graphix.Graphix
import com.strange.graphix.GraphixRequest
import com.strange.graphix.http.BadGraphixHttp
import com.strange.graphix.http.GRAPHQL_TRANSPORT_WS
import com.strange.graphix.http.GraphixHttpError
import com.strange.graphix.http.GraphixHttpRequest
import com.strange.graphix.http.GraphixHttpResponse
import com.strange.graphix.http.SubscriptionProtocol
import com.strange.graphix.http.toGraphixRequest
import com.strange.graphix.http.toHttp
import com.strange.graphix.http.toSse
import com.strange.graphix.isSubscription
import com.strange.graphix.subscribe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** POST and GET at [path]. Field errors stay HTTP 200; malformed JSON is 400. */
internal fun Route.graphqlRoute(
    path: String,
    engine: Graphix,
    json: Json,
    subscriptions: SubscriptionProtocol,
) {
    route(path) {
        post { call.handlePost(engine, json, subscriptions) }
        get { call.handleGet(engine, json, subscriptions) }
        if (subscriptions == SubscriptionProtocol.GraphqlWs) {
            webSocket(protocol = GRAPHQL_TRANSPORT_WS) { handleGraphqlWs(engine, json) }
        }
    }
}

private suspend fun ApplicationCall.handlePost(
    engine: Graphix,
    json: Json,
    subscriptions: SubscriptionProtocol,
) {
    val body = receiveText()
    val request =
        try {
            json.decodeFromString(GraphixHttpRequest.serializer(), body).toGraphixRequest()
        } catch (failure: SerializationException) {
            return respondBadRequest(json, "malformed GraphQL JSON: ${failure.message}")
        } catch (failure: BadGraphixHttp) {
            return respondBadRequest(json, failure.message ?: "malformed GraphQL request")
        }
    respondResult(engine, json, request, subscriptions)
}

private suspend fun ApplicationCall.handleGet(
    engine: Graphix,
    json: Json,
    subscriptions: SubscriptionProtocol,
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
        GraphixHttpRequest(
            query = query,
            operationName = request.queryParameters["operationName"],
            variables = variables,
        ).toGraphixRequest()
    respondResult(engine, json, request, subscriptions)
}

private suspend fun ApplicationCall.respondResult(
    engine: Graphix,
    json: Json,
    request: GraphixRequest,
    subscriptions: SubscriptionProtocol,
) {
    if (request.isSubscription()) {
        if (subscriptions == SubscriptionProtocol.GraphqlWs) {
            return respondBadRequest(json, "subscriptions use graphql-ws")
        }
        respondTextWriter(ContentType.Text.EventStream) {
            engine.subscribe(request).collect { result ->
                append(result.toHttp().toSse(json))
                flush()
            }
        }
        return
    }
    val result = engine.execute(request).toHttp()
    respondText(
        json.encodeToString(GraphixHttpResponse.serializer(), result),
        ContentType.Application.Json,
        HttpStatusCode.OK,
    )
}

private suspend fun ApplicationCall.respondBadRequest(
    json: Json,
    message: String,
) {
    val body = GraphixHttpResponse(errors = listOf(GraphixHttpError(message)))
    respondText(
        json.encodeToString(GraphixHttpResponse.serializer(), body),
        ContentType.Application.Json,
        HttpStatusCode.BadRequest,
    )
}
