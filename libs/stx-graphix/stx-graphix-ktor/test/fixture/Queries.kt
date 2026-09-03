package com.softistx.graphix.ktor.fixture

import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.GraphQLContext
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SubscriptionMapping
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

class GreetingQueries {
    @QueryMapping
    fun hello(): String = "world"
}

class BoomQueries {
    @QueryMapping
    fun boom(): String = throw IllegalStateException("nope")
}

/** A scalar argument, so a coercion error is what the Accept-Language spec below reads. */
class ExpiryQueries {
    @QueryMapping
    fun expiry(
        @Argument at: LocalDate,
    ): LocalDate = at
}

class TickSubscriptions {
    @SubscriptionMapping
    fun ticks(): Flow<Int> = flowOf(1, 2, 3)
}

/** The call as a plain resolver parameter — no annotation, because the plugin registered the type. */
class CallerQueries {
    @QueryMapping
    fun me(call: ApplicationCall): String = call.request.headers["X-User"] ?: "anonymous"
}

class CallerSubscriptions {
    @SubscriptionMapping
    fun callers(call: ApplicationCall): Flow<String> = flowOf(call.request.headers["X-User"] ?: "anonymous")
}

/** Reads what an interceptor put in the operation context rather than the call itself. */
data class Caller(
    val name: String,
)

class ContextQueries {
    @QueryMapping
    fun who(
        @GraphQLContext caller: Caller,
    ): String = caller.name
}
