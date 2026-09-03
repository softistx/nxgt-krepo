package com.softistx.graphix.fixture

import com.softistx.graphix.schema.BatchMapping
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SubscriptionMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Thrown by every fixture here, so a spec can assert it survived unwrapping. */
open class Boom(
    message: String = "boom",
) : RuntimeException(message)

/** Its subclass, for the most-specific-handler-wins rule. */
class SmallBoom : Boom("small boom")

/**
 * The **non-suspend** throw. `callBy` is plain reflection, so this one arrives at graphql-java
 * wrapped in `InvocationTargetException` — the reason the dispatch unwraps two wrapper types and
 * not one.
 */
class BlockingBoomQueries {
    @QueryMapping
    fun bang(): String = throw Boom()
}

/** The **suspend** throw, which travels as a failed `CompletableFuture` instead. */
class SuspendBoomQueries {
    @QueryMapping
    suspend fun bang(): String = throw Boom()
}

class SmallBoomQueries {
    @QueryMapping
    fun bang(): String = throw SmallBoom()
}

/** A `@BatchMapping` that throws: the DataLoader's future fails rather than the fetcher. */
class BatchBoomQueries {
    @QueryMapping
    fun products(): List<Product> = listOf(Product("1", "Kettle"))

    @BatchMapping
    fun reviews(products: List<Product>): Map<Product, List<Review>> = throw Boom()
}

/** Throws while emitting, not while subscribing — the two are different code paths. */
class MidStreamBoomSubscriptions {
    @SubscriptionMapping
    fun ticks(): Flow<Int> =
        flow {
            emit(1)
            throw Boom()
        }
}

/** Throws before returning the flow at all. */
class SubscribeBoomSubscriptions {
    @SubscriptionMapping
    fun ticks(): Flow<Int> = throw Boom()
}
