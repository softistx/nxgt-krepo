package com.strange.graphix.execute

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.future.future
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import java.util.concurrent.Flow as JdkFlow

/**
 * graphql-java's subscription strategy wants a `Publisher` (or JDK `Flow.Publisher`).
 * A Kotlin `Flow` is converted with `asPublisher` on the operation scope so cancelling
 * [com.strange.graphix.Graphix.subscribe] cancels the upstream.
 */
internal fun subscriptionFetcher(
    instance: Any,
    function: KFunction<*>,
    bind: (DataFetchingEnvironment) -> Map<KParameter, Any?>,
): DataFetcher<*> =
    DataFetcher { environment ->
        val scope =
            environment.graphQlContext.get<CoroutineScope>(OperationScope)
                ?: error("no CoroutineScope in GraphQLContext — Graphix.subscribe must install one")
        scope.future {
            val arguments = LinkedHashMap<KParameter, Any?>()
            val instanceParameter =
                function.instanceParameter
                    ?: error("${function.name} is not a member function")
            arguments[instanceParameter] = instance
            arguments.putAll(bind(environment))
            val value =
                if (function.isSuspend) {
                    function.callSuspendBy(arguments)
                } else {
                    function.callBy(arguments)
                }
            toPublisher(value, scope)
        }
    }

private fun toPublisher(
    value: Any?,
    scope: CoroutineScope,
): Any? =
    when (value) {
        null -> {
            null
        }

        is Publisher<*> -> {
            value
        }

        is JdkFlow.Publisher<*> -> {
            value
        }

        is Flow<*> -> {
            @Suppress("UNCHECKED_CAST")
            (value as Flow<Any>).asPublisher(scope.coroutineContext)
        }

        else -> {
            error("@SubscriptionMapping must return Flow or Publisher, got ${value::class.qualifiedName}")
        }
    }
