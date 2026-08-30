package com.strange.graphix.spring.fixture

import com.strange.graphix.schema.Query
import com.strange.graphix.schema.Subscription
import com.strange.graphix.spring.GraphQLController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@GraphQLController
class GreetingQueries {
    @Query
    fun hello(): String = "world"
}

@GraphQLController
class BoomQueries {
    @Query
    fun boom(): String = throw IllegalStateException("nope")
}

@GraphQLController
class TickSubscriptions {
    @Subscription
    fun ticks(): Flow<Int> = flowOf(1, 2, 3)
}
