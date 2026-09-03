package com.softistx.graphix.execute

import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.message.GraphixMessages
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
    messages: GraphixMessages = GraphixMessages.Bundled,
): ExecutionInput {
    val complexity =
        (context[GraphixLimits::class] as? GraphixLimits)?.toJava()
            ?: validation?.complexityLimits
    // A single operation may bring its own message source. Read out of the bag rather than left to
    // win by insertion order, so that the engine's keys can go in last without silencing it.
    val operationMessages = (context[GraphixMessages::class] as? GraphixMessages) ?: messages
    val builder =
        ExecutionInput
            .newExecutionInput()
            .query(request.query)
            .variables(request.variables)
            .operationName(request.operationName)
            // graphql-java hands this locale to every Coercing. Unset, it is the JVM's default,
            // which is the host's environment deciding what language a client is answered in.
            .apply { request.locale?.let { locale(it) } }
            .apply { if (request.extensions.isNotEmpty()) extensions(request.extensions) }
            .graphQLContext { graphQLContext ->
                // The caller's bag goes in first and the engine's own keys after it: every data
                // fetcher errors without `OperationScope`, and an interceptor contributing to the
                // context must not be able to unmake the operation by reusing the key. What is
                // genuinely per-operation — the message source, the complexity limits — is read out
                // of the bag above instead of winning by insertion order.
                context.forEach { (key, value) -> graphQLContext.put(key, value) }
                graphQLContext.put(OperationScope, scope)
                graphQLContext.put(GraphixMessages::class, operationMessages)
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
            }
    if (loaders.isNotEmpty()) {
        builder.dataLoaderRegistry(dataLoaderRegistry(loaders, scope, context))
    }
    return builder.build()
}
