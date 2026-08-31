package com.strange.graphix.execute

import com.strange.graphix.GraphixRequest
import com.strange.graphix.validation.GraphixLimits
import com.strange.graphix.validation.GraphixValidation
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.SubscriptionExecutionStrategy
import graphql.validation.QueryComplexityLimits
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

internal fun executionInput(
    request: GraphixRequest,
    context: Map<KClass<*>, Any>,
    scope: CoroutineScope,
    loaders: List<RegisteredLoader> = emptyList(),
    validation: GraphixValidation? = null,
): ExecutionInput {
    val complexity =
        (context[GraphixLimits::class] as? GraphixLimits)?.toJava()
            ?: validation?.complexityLimits
    val builder =
        ExecutionInput
            .newExecutionInput()
            .query(request.query)
            .variables(request.variables)
            .operationName(request.operationName)
            .graphQLContext { graphQLContext ->
                graphQLContext.put(OperationScope, scope)
                graphQLContext.put(SubscriptionExecutionStrategy.KEEP_SUBSCRIPTION_EVENTS_ORDERED, true)
                if (complexity != null) {
                    graphQLContext.put(QueryComplexityLimits.KEY, complexity)
                }
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
