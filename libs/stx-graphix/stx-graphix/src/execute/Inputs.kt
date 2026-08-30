package com.strange.graphix.execute

import com.strange.graphix.GraphixRequest
import graphql.ExecutionInput
import graphql.execution.SubscriptionExecutionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

internal fun executionInput(
    request: GraphixRequest,
    context: Map<KClass<*>, Any>,
    scope: CoroutineScope,
    batches: List<BatchBinding> = emptyList(),
): ExecutionInput {
    val builder =
        ExecutionInput
            .newExecutionInput()
            .query(request.query)
            .variables(request.variables)
            .operationName(request.operationName)
            .graphQLContext { graphQLContext ->
                graphQLContext.put(OperationScope, scope)
                graphQLContext.put(SubscriptionExecutionStrategy.KEEP_SUBSCRIPTION_EVENTS_ORDERED, true)
                context.forEach { (key, value) -> graphQLContext.put(key, value) }
            }
    if (batches.isNotEmpty()) {
        builder.dataLoaderRegistry(dataLoaderRegistry(batches, scope, context))
    }
    return builder.build()
}
