package com.strange.graphix.ktor.fixture

import com.strange.graphix.schema.Query
import com.strange.graphix.schema.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class GreetingQueries {
    @Query
    fun hello(): String = "world"
}

class BoomQueries {
    @Query
    fun boom(): String = throw IllegalStateException("nope")
}

class TickSubscriptions {
    @Subscription
    fun ticks(): Flow<Int> = flowOf(1, 2, 3)
}
