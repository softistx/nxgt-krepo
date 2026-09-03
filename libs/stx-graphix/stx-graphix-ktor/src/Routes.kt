package com.softistx.graphix.ktor

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.http.BadGraphixHttp
import com.softistx.graphix.http.GRAPHQL_TRANSPORT_WS
import com.softistx.graphix.http.GraphixHttpError
import com.softistx.graphix.http.GraphixHttpRequest
import com.softistx.graphix.http.GraphixHttpResponse
import com.softistx.graphix.http.SubscriptionProtocol
import com.softistx.graphix.http.acceptedLocale
import com.softistx.graphix.http.toGraphixRequest
import com.softistx.graphix.http.toHttp
import com.softistx.graphix.http.toSse
import com.softistx.graphix.isSubscription
import com.softistx.graphix.subscribe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import java.util.Locale

/**
 * The Apollo Sandbox page. A **sibling** of the GraphQL path, not a child, so it is its own
 * registration: [graphqlRoute] nests everything inside one `route(path)` block and nothing under
 * `/graphql` should answer HTML.
 */
internal fun Route.sandboxRoute(
    sandboxPath: String,
    html: String,
) {
    get(sandboxPath) { call.respondText(html, ContentType.Text.Html, HttpStatusCode.OK) }
}

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
            json.decodeFromString(GraphixHttpRequest.serializer(), body).toGraphixRequest(preferredLocale())
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
        ).toGraphixRequest(preferredLocale())
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

/** The call's `Accept-Language`, as the locale every coercion error on it is translated in. */
private fun ApplicationCall.preferredLocale(): Locale? = acceptedLocale(request.headers[HttpHeaders.AcceptLanguage])
