package com.softistx.graphix.execute

import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.validation.GraphixLimits
import com.softistx.graphix.validation.GraphixValidation
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.SubscriptionExecutionStrategy
import graphql.introspection.Introspection
import graphql.validation.QueryComplexityLimits
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

internal fun executionInput(
    request: GraphixRequest,
    context: Map<KClass<*>, Any>,
    scope: CoroutineScope,
    loaders: List<RegisteredLoader> = emptyList(),
    validation: GraphixValidation? = null,
    introspection: Boolean = true,
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
            .apply { if (request.extensions.isNotEmpty()) extensions(request.extensions) }
            .graphQLContext { graphQLContext ->
                graphQLContext.put(OperationScope, scope)
                graphQLContext.put(SubscriptionExecutionStrategy.KEEP_SUBSCRIPTION_EVENTS_ORDERED, true)
                // Per operation, not a JVM-wide switch: the schema still has __schema, it just refuses.
                if (!introspection) graphQLContext.put(Introspection.INTROSPECTION_DISABLED, true)
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
