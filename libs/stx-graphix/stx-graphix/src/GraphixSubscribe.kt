package com.strange.graphix

import com.strange.graphix.execute.executionInput
import com.strange.graphix.execute.toGraphixResult
import graphql.ExecutionResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import org.reactivestreams.Publisher
import kotlin.reflect.KClass

/**
 * Runs one subscription. Each event is a [GraphixResult] with the same `{ data, errors }`
 * shape as [Graphix.execute]. Cancelling the collector cancels the upstream `Flow` / `Publisher`.
 *
 * A query or mutation passed here throws [GraphixException] — use [Graphix.execute].
 */
fun Graphix.subscribe(
    request: GraphixRequest,
    context: Map<KClass<*>, Any> = emptyMap(),
): Flow<GraphixResult> =
    flow {
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val scope = CoroutineScope(currentCoroutineContext() + job + CoroutineName("graphql-subscription"))
        try {
            val result = engine.executeAsync(executionInput(request, context, scope, batches)).await()
            val data = result.getData<Any?>()
            if (data is Publisher<*>) {
                @Suppress("UNCHECKED_CAST")
                emitAll((data as Publisher<ExecutionResult>).asFlow().map { it.toGraphixResult() })
            } else if (result.errors.isNotEmpty()) {
                emit(result.toGraphixResult())
            } else {
                throw GraphixException("Graphix.subscribe is for subscription operations — use execute")
            }
        } finally {
            job.cancel()
        }
    }
