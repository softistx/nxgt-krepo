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
): ExecutionInput =
    ExecutionInput
        .newExecutionInput()
        .query(request.query)
        .variables(request.variables)
        .operationName(request.operationName)
        .graphQLContext { builder ->
            builder.put(OperationScope, scope)
            builder.put(SubscriptionExecutionStrategy.KEEP_SUBSCRIPTION_EVENTS_ORDERED, true)
            context.forEach { (key, value) -> builder.put(key, value) }
        }.build()
