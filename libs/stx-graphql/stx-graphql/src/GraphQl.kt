package com.strange.graphql

import com.strange.common.serialization.lenientJson
import com.strange.graphql.execute.OperationScope
import com.strange.graphql.execute.toGraphQlResult
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
class GraphQl internal constructor(
    private val engine: GraphQL,
) {
    suspend fun execute(
        request: GraphQlRequest,
        context: Map<KClass<*>, Any> = emptyMap(),
    ): GraphQlResult {
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
            result.toGraphQlResult()
        } finally {
            job.cancel()
        }
    }

    fun sdl(): String = SchemaPrinter().print(engine.graphQLSchema)
}

class GraphQlBuilder internal constructor(
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

    internal fun build(): GraphQl {
        val schema = graphQLSchema(queries, mutations, json)
        return GraphQl(GraphQL.newGraphQL(schema).build())
    }
}

fun GraphQl(
    json: Json = lenientJson,
    block: GraphQlBuilder.() -> Unit,
): GraphQl = GraphQlBuilder(json).apply(block).build()
