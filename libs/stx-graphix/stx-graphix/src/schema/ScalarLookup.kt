package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import com.strange.graphix.scalar.Scalars
import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLFloat
import graphql.Scalars.GraphQLID
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

/** Spec and Kotlin scalars by `KClass`. `null` means keep walking the SerialDescriptor. */
@OptIn(ExperimentalUuidApi::class)
internal fun scalarFromClass(
    kType: KType,
    extras: Map<KClass<*>, GraphQLScalarType> = emptyMap(),
): GraphQLScalarType? {
    val classifier = kType.classifier as? KClass<*> ?: return null
    extras[classifier]?.let { return it }
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

/**
 * GraphQL's `ID` for a `@GraphQLId` element, or `null` when the type still needs walking — a
 * `List<String>` carries the annotation down to its element. Anything else is a build failure.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun idScalar(kType: KType): GraphQLScalarType? {
    val classifier = kType.classifier as? KClass<*> ?: return null
    return when (classifier) {
        String::class, kotlin.uuid.Uuid::class, Long::class -> GraphQLID

        List::class -> null

        else -> throw GraphixException(
            "@GraphQLId is only for String, Uuid or Long, not ${classifier.qualifiedName}",
        )
    }
}

/** Same table as [scalarFromClass], keyed by `SerialDescriptor.serialName`. */
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

/** Wraps in GraphQL NonNull when [nullable] is false. */
internal fun wrapOutput(
    type: GraphQLOutputType,
    nullable: Boolean,
): GraphQLOutputType = if (nullable) type else GraphQLNonNull.nonNull(type)

/** Same wrapping as [wrapOutput], for input types. */
internal fun wrapInput(
    type: GraphQLInputType,
    nullable: Boolean,
): GraphQLInputType = if (nullable) type else GraphQLNonNull.nonNull(type)
