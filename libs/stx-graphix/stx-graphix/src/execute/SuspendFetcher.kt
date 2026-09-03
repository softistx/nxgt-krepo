package com.softistx.graphix.execute

import com.softistx.graphix.schema.streamElement
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.future.future
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspendBy

/**
 * graphql-java speaks CompletableFuture. A resolver is a suspend function. `future { }` is the
 * bridge — never `runBlocking`, which would park the engine thread the way stx-jpa refuses to.
 */
internal fun suspendFetcher(
    instance: Any,
    function: KFunction<*>,
    bind: (DataFetchingEnvironment) -> Map<kotlin.reflect.KParameter, Any?>,
): DataFetcher<*> {
    // Decided once, from the declared type, rather than by looking at every value the resolver
    // returns: a field that is not a stream keeps exactly the two branches below, and the two
    // invariants they carry are untouched because a `CompletionStage` return is not a stream.
    val collects = function.returnType.streamElement() != null
    return DataFetcher { environment ->
        val scope =
            environment.graphQlContext.get<CoroutineScope>(OperationScope)
                ?: error("no CoroutineScope in GraphQLContext — Graphix.execute must install one")
        val arguments = function.argumentsWith(instance, bind(environment))
        when {
            // A stream has to be collected, and collecting suspends — so this branch needs a
            // coroutine whether or not the resolver itself has one. That is the case a developer
            // here actually wrote: a plain `fun` returning `Flow`.
            collects -> {
                scope.future(
                    context = DataFetchingEnvironmentElement(environment),
                    start = CoroutineStart.UNDISPATCHED,
                ) {
                    val value = if (function.isSuspend) function.callSuspendBy(arguments) else function.callBy(arguments)
                    collectStream(value, environment.field.name, environment.maxListElements())
                }
            }

            // UNDISPATCHED so Loader.load queues the key before DataFetcher.get returns —
            // otherwise the level dispatches an empty DataLoader and sibling fields do not batch.
            function.isSuspend -> {
                scope.future(
                    context = DataFetchingEnvironmentElement(environment),
                    start = CoroutineStart.UNDISPATCHED,
                ) {
                    function.callSuspendBy(arguments)
                }
            }

            // Return CompletionStage as-is — wrapping DataLoader.load in future { } never completes.
            else -> {
                function.callBy(arguments)
            }
        }
    }
}
