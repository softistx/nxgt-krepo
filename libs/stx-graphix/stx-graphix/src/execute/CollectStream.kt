package com.softistx.graphix.execute

import com.softistx.graphix.TooManyElements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.reactivestreams.FlowAdapters
import org.reactivestreams.Publisher
import java.util.concurrent.Flow as JdkFlow

/**
 * A stream a resolver returned where the schema says a list, as the list graphql-java wants.
 *
 * Its list completion takes an `Iterable`, a `Stream`, an `Iterator` or an array, and a `Flow` is
 * none of those — which is the whole of `Can't resolve value : type mismatch error, expected type
 * LIST`, the failure this exists to end.
 *
 * The opposite direction to `SubscriptionFetcher`'s `toPublisher`: there a `Flow` becomes a
 * `Publisher` of *separate responses*; here it becomes one `List` inside a single response.
 *
 * **This is not streaming.** graphql-java 26 defines a `defer` directive and no `stream` one, so a
 * client waits for the whole list either way. What is bought is not keystrokes: the collection
 * happens on the operation's `CoroutineScope`, so cancelling the request cancels the source, and
 * inside the data fetcher, so a throw reaches the exception handlers like any other resolver throw.
 *
 * A value that is not a stream is returned untouched — the declared type said one thing and the
 * value said another, and graphql-java's own complaint beats one invented here.
 */
internal suspend fun collectStream(
    value: Any?,
    field: String,
    max: Int?,
): Any? {
    val flow: Flow<*> =
        when (value) {
            is Flow<*> -> value

            is Publisher<*> -> value.asFlow()

            // reactive-streams ships the bridge, so the JDK shape costs no dependency.
            is JdkFlow.Publisher<*> -> FlowAdapters.toPublisher(value).asFlow()

            else -> return value
        }
    if (max == null) return flow.toList()
    // `take` and not a throw from inside `collect`: it aborts the upstream with kotlinx's own
    // mechanism, which an ill-written `flow { }` catching around its `emit` cannot swallow. One
    // extra element is buffered so that "exactly max" and "more than max" stay distinguishable.
    val collected = flow.take(max + 1).toList()
    if (collected.size > max) throw TooManyElements(field, max)
    return collected
}
