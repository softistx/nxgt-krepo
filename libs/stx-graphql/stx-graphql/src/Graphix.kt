package com.strange.graphql

import com.strange.common.serialization.lenientJson
import com.strange.graphql.execute.OperationScope
import com.strange.graphql.execute.toGraphixResult
import com.strange.graphql.schema.graphQLSchema
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

/**
 * A GraphQL engine built from annotated Kotlin functions and `@Serializable` types.
 *
 * The application names its roots — there is no classpath scan here. Spring may scan; this type
 * does not.
 */
class Graphix internal constructor(
    private val engine: GraphQL,
) {
    suspend fun execute(
        request: GraphixRequest,
        context: Map<KClass<*>, Any> = emptyMap(),
    ): GraphixResult {
        val job = SupervisorJob(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + job + CoroutineName("graphql"))
        val input =
            ExecutionInput
                .newExecutionInput()
                .query(request.query)
                .variables(request.variables)
                .operationName(request.operationName)
                .graphQLContext { builder ->
                    builder.put(OperationScope, scope)
                    context.forEach { (key, value) -> builder.put(key, value) }
                }.build()
        return try {
            val result = engine.executeAsync(input).await()
            result.toGraphixResult()
        } finally {
            job.cancel()
        }
    }

    fun sdl(): String = SchemaPrinter().print(engine.graphQLSchema)
}

class GraphixBuilder internal constructor(
    private val json: Json,
) {
    private val queries = mutableListOf<Any>()
    private val mutations = mutableListOf<Any>()

    fun query(instance: Any) {
        queries += instance
    }

    fun mutation(instance: Any) {
        mutations += instance
    }

    internal fun build(): Graphix {
        val schema = graphQLSchema(queries, mutations, json)
        return Graphix(GraphQL.newGraphQL(schema).build())
    }
}

fun Graphix(
    json: Json = lenientJson,
    block: GraphixBuilder.() -> Unit,
): Graphix = GraphixBuilder(json).apply(block).build()
