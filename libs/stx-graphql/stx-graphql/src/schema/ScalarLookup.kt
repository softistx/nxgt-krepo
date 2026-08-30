package com.strange.graphql.schema

import com.strange.graphql.scalar.Scalars
import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLFloat
import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLScalarType
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal fun scalarFromClass(kType: KType): GraphQLScalarType? {
    val classifier = kType.classifier as? KClass<*> ?: return null
    return when (classifier) {
        String::class -> GraphQLString
        Int::class -> GraphQLInt
        Boolean::class -> GraphQLBoolean
        Double::class, Float::class -> GraphQLFloat
        Long::class -> Scalars.Long
        kotlin.time.Instant::class -> Scalars.Instant
        kotlin.uuid.Uuid::class -> Scalars.Uuid
        else -> null
    }
}

internal fun scalarOf(descriptor: SerialDescriptor): GraphQLScalarType? =
    when (descriptor.serialName) {
        "kotlin.String", "String" -> {
            GraphQLString
        }

        "kotlin.Int", "Int" -> {
            GraphQLInt
        }

        "kotlin.Boolean", "Boolean" -> {
            GraphQLBoolean
        }

        "kotlin.Double", "Double", "kotlin.Float", "Float" -> {
            GraphQLFloat
        }

        "kotlin.Long", "Long" -> {
            Scalars.Long
        }

        "kotlin.time.Instant" -> {
            Scalars.Instant
        }

        "kotlin.uuid.Uuid" -> {
            Scalars.Uuid
        }

        else -> {
            when (descriptor.kind) {
                PrimitiveKind.STRING -> GraphQLString
                PrimitiveKind.INT -> GraphQLInt
                PrimitiveKind.BOOLEAN -> GraphQLBoolean
                PrimitiveKind.DOUBLE, PrimitiveKind.FLOAT -> GraphQLFloat
                PrimitiveKind.LONG -> Scalars.Long
                else -> null
            }
        }
    }

internal fun wrapOutput(
    type: GraphQLOutputType,
    nullable: Boolean,
): GraphQLOutputType = if (nullable) type else GraphQLNonNull.nonNull(type)

internal fun wrapInput(
    type: GraphQLInputType,
    nullable: Boolean,
): GraphQLInputType = if (nullable) type else GraphQLNonNull.nonNull(type)
