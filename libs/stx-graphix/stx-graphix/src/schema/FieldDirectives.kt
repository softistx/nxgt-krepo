package com.strange.graphix.schema

import com.strange.graphix.GraphixBuilder
import com.strange.graphix.execute.OperationScope
import graphql.GraphQLContext
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaDirectiveWiringEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletionStage
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation

/** Suspend wrapper around a field. [proceed] is the original fetcher. */
typealias FieldDirectiveWrap = suspend FieldDirectiveScope.() -> Any?

/**
 * A named field directive. Spring collects these as beans; Ktor can `provide` them.
 *
 * [wrap] sees this field's [DataFetchingEnvironment] and the operation [GraphQLContext].
 */
class GraphixDirective(
    val name: String,
    val wrap: FieldDirectiveWrap,
)

/** Receiver for [FieldDirectiveWrap]. */
class FieldDirectiveScope(
    val environment: DataFetchingEnvironment,
    val arguments: Map<String, Any?>,
    private val next: suspend () -> Any?,
) {
    val graphQlContext: GraphQLContext get() = environment.graphQlContext

    suspend fun proceed(): Any? = next()
}

fun GraphixBuilder.fieldDirective(directive: GraphixDirective) {
    addFieldDirective(directive)
}

fun GraphixBuilder.fieldDirective(
    name: String,
    wrap: FieldDirectiveWrap,
) {
    fieldDirective(GraphixDirective(name, wrap))
}

internal fun DataFetcher<*>.withDirectives(
    function: KFunction<*>,
    directives: Map<String, FieldDirectiveWrap>,
): DataFetcher<*> {
    val applied = function.findAnnotation<Directive>() ?: return this
    val wrap =
        directives[applied.name]
            ?: error("no field directive '${applied.name}'")
    return directiveFetcher(this, wrap, emptyMap())
}

internal fun directiveFetcher(
    original: DataFetcher<*>,
    wrap: FieldDirectiveWrap,
    arguments: Map<String, Any?>,
): DataFetcher<*> =
    DataFetcher { environment ->
        val scope =
            environment.graphQlContext.get<CoroutineScope>(OperationScope)
                ?: error("no CoroutineScope in GraphQLContext — Graphix.execute must install one")
        scope.future(start = CoroutineStart.UNDISPATCHED) {
            val proceed: suspend () -> Any? = {
                when (val value = original.get(environment)) {
                    is CompletionStage<*> -> value.await()
                    else -> value
                }
            }
            FieldDirectiveScope(environment, arguments, proceed).wrap()
        }
    }

/**
 * A field directive wraps a data fetcher, so the only honest meaning for the same directive on an
 * OBJECT or an INTERFACE is "every field of it". `ARGUMENT_DEFINITION` and `INPUT_FIELD_DEFINITION`
 * transform a value rather than wrap a fetcher and are not supported.
 */
internal fun GraphixDirective.toSchemaWiring(): SchemaDirectiveWiring =
    object : SchemaDirectiveWiring {
        override fun onField(environment: SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition>): GraphQLFieldDefinition {
            environment.setFieldDataFetcher(
                directiveFetcher(environment.fieldDataFetcher, wrap, environment.appliedArguments()),
            )
            return environment.element
        }

        override fun onObject(environment: SchemaDirectiveWiringEnvironment<GraphQLObjectType>): GraphQLObjectType {
            environment.wrapEveryField(environment.element.name, environment.element.fieldDefinitions)
            return environment.element
        }

        override fun onInterface(environment: SchemaDirectiveWiringEnvironment<GraphQLInterfaceType>): GraphQLInterfaceType {
            environment.wrapEveryField(environment.element.name, environment.element.fieldDefinitions)
            return environment.element
        }

        private fun SchemaDirectiveWiringEnvironment<*>.wrapEveryField(
            typeName: String,
            fields: List<GraphQLFieldDefinition>,
        ) {
            val args = appliedArguments()
            val registry = codeRegistry
            fields.forEach { field ->
                val coordinates = FieldCoordinates.coordinates(typeName, field.name)
                val original = registry.getDataFetcher(coordinates, field)
                registry.dataFetcher(coordinates, directiveFetcher(original, wrap, args))
            }
        }

        private fun SchemaDirectiveWiringEnvironment<*>.appliedArguments(): Map<String, Any?> =
            appliedDirective.arguments.associate { argument -> argument.name to argument.argumentValue.value }
    }
