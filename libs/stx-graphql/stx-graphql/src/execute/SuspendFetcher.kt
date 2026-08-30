package com.strange.graphql.execute

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter

/**
 * graphql-java speaks CompletableFuture. A resolver is a suspend function. `future { }` is the
 * bridge — never `runBlocking`, which would park the engine thread the way stx-jpa refuses to.
 */
internal fun suspendFetcher(
    instance: Any,
    function: KFunction<*>,
    bind: (DataFetchingEnvironment) -> Map<kotlin.reflect.KParameter, Any?>,
): DataFetcher<*> =
    DataFetcher { environment ->
        val scope =
            environment.graphQlContext.get<CoroutineScope>(OperationScope)
                ?: error("no CoroutineScope in GraphQLContext — Graphix.execute must install one")
        scope.future {
            val arguments = LinkedHashMap<kotlin.reflect.KParameter, Any?>()
            val instanceParameter =
                function.instanceParameter
                    ?: error("${function.name} is not a member function")
            arguments[instanceParameter] = instance
            arguments.putAll(bind(environment))
            if (function.isSuspend) {
                function.callSuspendBy(arguments)
            } else {
                function.callBy(arguments)
            }
        }
    }
