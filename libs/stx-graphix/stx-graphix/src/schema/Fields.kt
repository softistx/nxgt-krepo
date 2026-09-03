package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters
import graphql.GraphQLContext as OperationContext

internal fun fieldDefinition(
    function: KFunction<*>,
    name: String,
    output: GraphQLOutputType,
    types: TypeMapper,
): GraphQLFieldDefinition {
    val builder =
        GraphQLFieldDefinition
            .newFieldDefinition()
            .name(name)
            .description(function.graphQLDescription())
            .type(output)
    function.graphQLDeprecation()?.let { builder.deprecate(it) }
    function.valueParameters.filter { it.isArgument() }.forEach { parameter ->
        val default = parameter.graphQLDefault("argument '${parameter.graphQLName()}' of $name")
        // A Kotlin default alone only makes the argument optional — graphql-java has no Kotlin
        // defaults. @GraphQLDefault puts the value in the schema and keeps the NonNull.
        val argumentType =
            types.input(parameter.type, parameter.isGraphQLId()).let { type ->
                if (default == null && parameter.isOptional && type is GraphQLNonNull) {
                    type.wrappedType as GraphQLInputType
                } else {
                    type
                }
            }
        val argument =
            GraphQLArgument
                .newArgument()
                .name(parameter.graphQLName())
                .description(parameter.graphQLDescription())
                .type(argumentType)
                .apply { if (default != null) defaultValueLiteral(default) }
        parameter.graphQLDeprecation()?.let { reason ->
            refuseRequiredDeprecation("argument '${parameter.graphQLName()}' of $name", argumentType, default != null)
            argument.deprecate(reason)
        }
        builder.argument(argument.build())
    }
    return builder.build()
}

internal fun KParameter.isArgument(): Boolean = hasAnnotation<Argument>()

internal fun KParameter.isDataFetchingEnvironment(): Boolean {
    val classifier = type.classifier as? KClass<*> ?: return false
    return classifier.isSubclassOf(DataFetchingEnvironment::class)
}

/** graphql-java's context bag itself, as a parameter. Needs no registration — it is graphql-java's. */
internal fun KParameter.isOperationContext(): Boolean {
    val classifier = type.classifier as? KClass<*> ?: return false
    return classifier.isSubclassOf(OperationContext::class)
}

/**
 * A parameter the framework supplies rather than the document: the DFE, the operation context, or
 * a type an integration registered with `contextParameter(...)` — `ApplicationCall` in Ktor,
 * `ServerWebExchange` in Spring.
 *
 * Registered rather than guessed. The core names no framework type, and a resolver that asks for
 * one the running stack did not register fails saying so instead of being handed nothing.
 */
internal fun KParameter.isFrameworkParameter(contextTypes: Set<KClass<*>>): Boolean {
    if (isDataFetchingEnvironment() || isOperationContext()) return true
    val classifier = type.classifier as? KClass<*> ?: return false
    return contextTypes.any { classifier.isSubclassOf(it) }
}

/**
 * Every value parameter is the parent source, a framework parameter, or `@Argument`. GraphQL
 * arguments must be marked. Input-object fields are not arguments.
 *
 * The alternative — treating anything unmarked as a context read — was rejected: a forgotten
 * `@Argument` would then publish a field with no argument at all, so the SDL a client reads would be
 * wrong and only the execution would say so.
 */
internal fun KFunction<*>.requireArgumentAnnotations(
    parent: KParameter? = null,
    contextTypes: Set<KClass<*>> = emptySet(),
) {
    valueParameters.forEach { parameter ->
        if (parameter == parent) return@forEach
        if (parameter.isFrameworkParameter(contextTypes) || parameter.isArgument()) return@forEach
        throw GraphixException(
            "$name parameter '${parameter.name}' must be @Argument, or a type registered with " +
                "contextParameter(...) to read it from the operation context. " +
                "The parent source, DataFetchingEnvironment and GraphQLContext take neither.",
        )
    }
}

/**
 * GraphQL forbids deprecating an input a caller has to send: there would be no way to stop sending
 * it. *Required* is non-null **and** without a default — `limit: Int! = 10` may be deprecated,
 * because omitting it still works.
 */
internal fun refuseRequiredDeprecation(
    what: String,
    type: GraphQLInputType,
    hasDefault: Boolean = false,
) {
    if (type is GraphQLNonNull && !hasDefault) {
        throw GraphixException(
            "$what is required, so it cannot be @GraphQLDeprecated — make it optional, or give it a @GraphQLDefault",
        )
    }
}
