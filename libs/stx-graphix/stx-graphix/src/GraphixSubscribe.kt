package com.softistx.graphix

import com.softistx.graphix.error.handleOutsideField
import com.softistx.graphix.execute.executionInput
import com.softistx.graphix.execute.toGraphixResult
import com.softistx.graphix.intercept.runChain
import graphql.ExecutionResult
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    runChain(interceptors, request, context) { operation, values -> subscribeOnce(operation, values) }
        .catch { failure ->
            // Covers both throws that never reach graphql-java: an interceptor's, and a `Flow` that
            // fails part-way through emitting. Unclaimed, it is rethrown and nothing changes.
            handleOutsideField(failure, request, context)?.let { emit(it) } ?: throw failure
        }

private fun Graphix.subscribeOnce(
    request: GraphixRequest,
    context: Map<KClass<*>, Any>,
): Flow<GraphixResult> =
    flow {
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val scope = CoroutineScope(currentCoroutineContext() + job + CoroutineName("graphql-subscription"))
        try {
            val result =
                engine.executeAsync(executionInput(request, context, scope, loaders, validation, introspection, messages)).await()
            val data = result.getData<Any>()
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
