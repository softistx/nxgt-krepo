package com.softistx.graphix.fixture

import com.softistx.graphix.http.GraphqlWsInit
import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.BatchMapping
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SchemaMapping
import com.softistx.graphix.schema.SubscriptionMapping
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import graphql.GraphQLContext as OperationContext

/**
 * Stands in for `ApplicationCall` and `ServerWebExchange`: a type the core knows nothing about,
 * registered with `contextParameter(...)` by whoever owns the HTTP request.
 */
class FakeCall(
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class Badge(
    val id: String,
)

class FrameworkParameterQueries {
    @QueryMapping
    fun me(call: FakeCall): String = call.headers["X-User"] ?: "anonymous"

    /** All three kinds of parameter on one function, to pin that they do not crowd each other out. */
    @QueryMapping
    fun stamp(
        call: FakeCall,
        environment: DataFetchingEnvironment,
        @Argument suffix: String,
    ): String = "${call.headers["X-User"]}:${environment.field.name}:$suffix"

    @QueryMapping
    fun badge(): Badge = Badge("t1")
}

/** graphql-java's own context bag, which needs no registration — it is graphql-java's, not a framework's. */
class ContextBagQueries {
    @QueryMapping
    fun who(context: OperationContext): String = context.get<Caller>(Caller::class)?.locale ?: "none"
}

/**
 * The framework parameter comes **first**, ahead of the parent. Schema build picks the parent as
 * the first parameter it does not supply itself, so this is the case that breaks if a registered
 * type is not counted as supplied.
 */
class BadgeFields {
    @SchemaMapping
    fun owner(
        call: FakeCall,
        badge: Badge,
    ): String = "${call.headers["X-User"] ?: "anonymous"}/${badge.id}"
}

/** Reads what the graphql-ws client sent with `connection_init`, and the socket's own context. */
class SocketQueries {
    @QueryMapping
    fun token(init: GraphqlWsInit): String = (init.payload as? JsonObject)?.get("authToken")?.jsonPrimitive?.content ?: "none"

    @QueryMapping
    fun handshake(call: FakeCall): String = call.headers["X-User"] ?: "anonymous"
}

class CountSubscriptions {
    @SubscriptionMapping
    fun counts(): Flow<Int> = flowOf(1, 2)
}

class CallerSubscriptions {
    @SubscriptionMapping
    fun callers(call: FakeCall): Flow<String> = flowOf(call.headers["X-User"] ?: "anonymous")
}

/**
 * A `@BatchMapping` taking a registered context type, with the framework parameter **first**.
 *
 * This is the shape that used to pass schema build and then be dropped: the batch path keyed off
 * `@GraphQLContext` alone and had no branch — and no `else` — for a registered type.
 */
class BadgeBatch {
    @BatchMapping
    fun stamp(
        call: FakeCall,
        badges: List<Badge>,
    ): Map<Badge, String> = badges.associateWith { "${call.headers["X-User"] ?: "anonymous"}/${it.id}" }
}

/** The same, for graphql-java's own context bag, which the batch path never checked either. */
class BadgeContextBatch {
    @BatchMapping
    fun tag(
        context: OperationContext,
        badges: List<Badge>,
    ): Map<Badge, String> = badges.associateWith { context.get<Caller>(Caller::class)?.locale ?: "none" }
}

/** A root parameter that is neither `@Argument` nor a registered type — refused at schema build. */
class UnregisteredQueries {
    @QueryMapping
    fun product(id: String): String = id
}
