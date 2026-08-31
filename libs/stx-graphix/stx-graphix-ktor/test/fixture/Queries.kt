package com.strange.graphix.ktor.fixture

import com.strange.graphix.schema.QueryMapping
import com.strange.graphix.schema.SubscriptionMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class GreetingQueries {
    @QueryMapping
    fun hello(): String = "world"
}

class BoomQueries {
    @QueryMapping
    fun boom(): String = throw IllegalStateException("nope")
}

class TickSubscriptions {
    @SubscriptionMapping
    fun ticks(): Flow<Int> = flowOf(1, 2, 3)
}
