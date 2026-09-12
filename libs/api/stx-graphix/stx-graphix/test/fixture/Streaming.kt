package com.softistx.graphix.fixture

import com.softistx.graphix.schema.GraphQLId
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SchemaMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactive.publish
import org.reactivestreams.Publisher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow as JdkFlow

/**
 * Resolvers returning a stream where the schema says a list — every shape `streamElement` accepts,
 * plus the ones that must still be refused.
 *
 * The plain `fun` forms are not an oversight: a non-suspend resolver returning a `Flow` is the case
 * a developer here actually wrote, and it is the branch of `suspendFetcher` with no coroutine of its
 * own to collect in.
 */
class StreamQueries {
    /** Not `suspend`, on purpose. */
    @QueryMapping
    fun names(): Flow<String> = flowOf("ada", "grace")

    @QueryMapping
    suspend fun suspendedNames(): Flow<String> = flowOf("ada", "grace")

    @QueryMapping
    fun published(): Publisher<String> = flowOf("ada", "grace").asPublisher()

    @QueryMapping
    fun jdkPublished(): JdkFlow.Publisher<String> = JdkPublisher(listOf("ada", "grace"))

    @QueryMapping
    fun empty(): Flow<String> = flowOf()

    @QueryMapping
    fun missing(): Flow<String>? = null

    @QueryMapping
    fun sparse(): Flow<String?> = flowOf("ada", null)

    @QueryMapping
    @GraphQLId
    fun ids(): Flow<String> = flowOf("p1", "p2")

    @QueryMapping
    fun objects(): Flow<Product> = flowOf(Product("p1", "Mug"))

    @QueryMapping
    fun counted(
        @com.softistx.graphix.schema.Argument to: Int,
    ): Flow<Int> = flow { repeat(to) { emit(it) } }

    /** Never completes, so only a bound can end the request it is in. */
    @QueryMapping
    fun endless(): Flow<Int> =
        flow {
            var index = 0
            while (true) emit(index++)
        }
}

/** A `Flow` on a **type** field, so the same collection has to reach `@SchemaMapping`. */
class StreamProductFields {
    @SchemaMapping
    fun aliases(product: Product): Flow<String> = flowOf("${product.name}-1", "${product.name}-2")
}

/**
 * A plain `fun` handing back a stage, which `suspendFetcher` must return **untouched**.
 *
 * The invariant is older than the streams above and had no spec: wrapping a `DataLoader.load`
 * future in `future { }` never completes, so the non-suspend branch returns what it got. Adding a
 * collecting branch beside it is exactly the change that could have lost it.
 */
class StagedQueries {
    @QueryMapping
    fun later(): CompletionStage<String> = CompletableFuture.supplyAsync { "eventually" }
}

/** Refused at schema build: the fetcher's non-suspend branch must hand a stage back untouched. */
class StagedStreamQueries {
    @QueryMapping
    fun names(): CompletionStage<Flow<String>> = CompletableFuture.completedFuture(flowOf("ada"))
}

/**
 * A JDK `Flow.Publisher` written by hand rather than adapted, so the spec proves the adapter and not
 * a round trip through the shape it started as.
 */
private class JdkPublisher(
    private val values: List<String>,
) : JdkFlow.Publisher<String> {
    override fun subscribe(subscriber: JdkFlow.Subscriber<in String>) {
        subscriber.onSubscribe(
            object : JdkFlow.Subscription {
                private var index = 0

                override fun request(count: Long) {
                    while (index < values.size) subscriber.onNext(values[index++])
                    subscriber.onComplete()
                }

                override fun cancel() = Unit
            },
        )
    }
}
