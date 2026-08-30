package com.strange.graphix.schema

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters

internal fun fieldDefinition(
    function: KFunction<*>,
    name: String,
    output: GraphQLOutputType,
    types: TypeMapper,
    skip: (KParameter) -> Boolean,
): GraphQLFieldDefinition {
    val builder =
        GraphQLFieldDefinition
            .newFieldDefinition()
            .name(name)
            .description(function.graphQLDescription())
            .type(output)
    function.valueParameters.filterNot(skip).forEach { parameter ->
        // A Kotlin default is still GraphQL NonNull unless unwrapped: graphql-java has no defaults.
        val argumentType =
            types.input(parameter.type).let { type ->
                if (parameter.isOptional && type is GraphQLNonNull) {
                    type.wrappedType as GraphQLInputType
                } else {
                    type
                }
            }
        builder.argument(
            GraphQLArgument
                .newArgument()
                .name(parameter.graphQLName())
                .description(parameter.findAnnotation<GraphQLDescription>()?.value)
                .type(argumentType)
                .build(),
        )
    }
    return builder.build()
}

internal fun KParameter.isGraphQLContext(): Boolean = hasAnnotation<GraphQLContext>()

internal fun KParameter.isLoad(): Boolean = hasAnnotation<Load>()
