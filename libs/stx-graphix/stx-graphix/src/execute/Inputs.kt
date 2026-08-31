package com.strange.graphix.execute

import com.strange.graphix.GraphixRequest
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.SubscriptionExecutionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

internal fun executionInput(
    request: GraphixRequest,
    context: Map<KClass<*>, Any>,
    scope: CoroutineScope,
    loaders: List<RegisteredLoader> = emptyList(),
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
                GraphQL
                    .unusualConfiguration(graphQLContext)
                    .dataloaderConfig()
                    .enableDataLoaderExhaustedDispatching(true)
                context.forEach { (key, value) -> graphQLContext.put(key, value) }
            }
    if (loaders.isNotEmpty()) {
        builder.dataLoaderRegistry(dataLoaderRegistry(loaders, scope, context))
    }
    return builder.build()
}
