package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import com.softistx.graphix.scalar.ScalarsByKotlinType
import com.softistx.graphix.scalar.ScalarsBySerialName
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

/**
 * GraphQL's own five, keyed by `KClass`. Everything past them is the scalar registry, which is
 * why adding a scalar does not touch this file.
 */
private val SpecScalars: Map<KClass<*>, GraphQLScalarType> =
    mapOf(
        String::class to GraphQLString,
        Int::class to GraphQLInt,
        Boolean::class to GraphQLBoolean,
        Double::class to GraphQLFloat,
        Float::class to GraphQLFloat,
    )

/** Spec and built-in scalars by `KClass`, [extras] first. `null` means keep walking the descriptor. */
internal fun scalarFromClass(
    kType: KType,
    extras: Map<KClass<*>, GraphQLScalarType> = emptyMap(),
): GraphQLScalarType? {
    val classifier = kType.classifier as? KClass<*> ?: return null
    // An application's own scalar for a class outranks a built-in one for the same class.
    extras[classifier]?.let { return it }
    return SpecScalars[classifier] ?: ScalarsByKotlinType[classifier]
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

/** Same tables as [scalarFromClass], keyed by `SerialDescriptor.serialName`. */
internal fun scalarOf(descriptor: SerialDescriptor): GraphQLScalarType? =
    when (descriptor.serialName) {
        "kotlin.String", "String" -> GraphQLString
        "kotlin.Int", "Int" -> GraphQLInt
        "kotlin.Boolean", "Boolean" -> GraphQLBoolean
        "kotlin.Double", "Double", "kotlin.Float", "Float" -> GraphQLFloat
        else -> ScalarsBySerialName[descriptor.serialName] ?: byKind(descriptor)
    }

/**
 * A descriptor nobody named: a custom serializer over a primitive. Its **kind** is then the only
 * thing that says what it is, and a serializer that calls itself `MoneyAsString` is a `String`.
 */
private fun byKind(descriptor: SerialDescriptor): GraphQLScalarType? =
    when (descriptor.kind) {
        PrimitiveKind.STRING -> GraphQLString
        PrimitiveKind.INT -> GraphQLInt
        PrimitiveKind.BOOLEAN -> GraphQLBoolean
        PrimitiveKind.DOUBLE, PrimitiveKind.FLOAT -> GraphQLFloat
        PrimitiveKind.LONG -> ScalarsBySerialName.getValue("kotlin.Long")
        PrimitiveKind.SHORT -> ScalarsBySerialName.getValue("kotlin.Short")
        PrimitiveKind.BYTE -> ScalarsBySerialName.getValue("kotlin.Byte")
        PrimitiveKind.CHAR -> ScalarsBySerialName.getValue("kotlin.Char")
        else -> null
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
