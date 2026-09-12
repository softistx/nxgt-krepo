package com.softistx.graphix.spring.fixture

import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SubscriptionMapping
import com.softistx.graphix.spring.GraphQLController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.springframework.web.server.ServerWebExchange

@GraphQLController
class GreetingQueries {
    @QueryMapping
    fun hello(): String = "world"
}

@GraphQLController
class BoomQueries {
    @QueryMapping
    fun boom(): String = throw IllegalStateException("nope")
}

@GraphQLController
class TickSubscriptions {
    @SubscriptionMapping
    fun ticks(): Flow<Int> = flowOf(1, 2, 3)
}

/** The exchange as a plain parameter — no annotation, because the auto-configuration registered the type. */
@GraphQLController
class ExchangeQueries {
    @QueryMapping
    fun me(exchange: ServerWebExchange): String = exchange.request.headers.getFirst("X-User") ?: "anonymous"
}

@GraphQLController
class ExchangeSubscriptions {
    @SubscriptionMapping
    fun callers(exchange: ServerWebExchange): Flow<String> = flowOf(exchange.request.headers.getFirst("X-User") ?: "anonymous")
}

/** Reads what an interceptor put in the operation context rather than the exchange itself. */
data class Caller(
    val name: String,
)

@GraphQLController
class ContextQueries {
    @QueryMapping
    fun who(caller: Caller): String = caller.name
}
