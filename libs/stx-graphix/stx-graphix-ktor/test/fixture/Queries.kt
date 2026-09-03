package com.softistx.graphix.ktor.fixture

import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SubscriptionMapping
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
