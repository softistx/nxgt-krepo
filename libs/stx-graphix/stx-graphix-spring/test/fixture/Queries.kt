package com.softistx.graphix.spring.fixture

import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SubscriptionMapping
import com.softistx.graphix.spring.GraphQLController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
