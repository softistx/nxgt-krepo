package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import com.strange.graphix.scalar.Scalars
import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLFloat
import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters
import kotlin.uuid.ExperimentalUuidApi

/**
 * SerialDescriptor is the type system. A Kotlin type that has no serializer is a schema-build
 * failure naming that type, not a silent Map.
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
internal class TypeMapper(
    private val serializers: SerializersModule,
) {
    private val outputs = linkedMapOf<String, GraphQLObjectType>()
    private val inputs = linkedMapOf<String, GraphQLInputObjectType>()
    private val enums = linkedMapOf<String, GraphQLEnumType>()
    private val building = mutableSetOf<String>()

    /** GraphQL output type for [kType], including nullability. */
    fun output(kType: KType): GraphQLOutputType = wrapOutput(mapOutput(kType), kType.isMarkedNullable)

    /** GraphQL input type for [kType], including nullability. */
    fun input(kType: KType): GraphQLInputType = wrapInput(mapInput(kType), kType.isMarkedNullable)

    /** Named object, input object and enum types this mapper built — for `additionalTypes`. */
    fun additionalTypes(): Set<GraphQLType> = (outputs.values + inputs.values + enums.values).toSet()

    private fun mapOutput(kType: KType): GraphQLOutputType {
        scalarFromClass(kType)?.let { return it }
        val descriptor = descriptorOf(kType)
        scalarOf(descriptor)?.let { return it }
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                GraphQLList.list(
                    output(kType.arguments.single().type ?: throw GraphixException("a list GraphQL type needs an element type: $kType")),
                )
            }

            SerialKind.ENUM -> {
                enumType(kType, descriptor)
            }

            StructureKind.CLASS, StructureKind.OBJECT -> {
                objectType(kType, descriptor)
            }

            is PrimitiveKind -> {
                throw GraphixException("unsupported primitive ${descriptor.kind} on $kType")
            }

            StructureKind.MAP -> {
                throw GraphixException("Map is not a GraphQL type: $kType")
            }

            is PolymorphicKind -> {
                throw GraphixException("polymorphic types are not GraphQL types yet: $kType")
            }

            else -> {
                throw GraphixException("cannot map ${descriptor.kind} as a GraphQL output type: $kType")
            }
        }
    }

    private fun mapInput(kType: KType): GraphQLInputType {
        scalarFromClass(kType)?.let { return it }
        val descriptor = descriptorOf(kType)
        scalarOf(descriptor)?.let { return it }
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                GraphQLList.list(
                    input(kType.arguments.single().type ?: throw GraphixException("a list GraphQL type needs an element type: $kType")),
                )
            }

            SerialKind.ENUM -> {
                enumType(kType, descriptor)
            }

            StructureKind.CLASS, StructureKind.OBJECT -> {
                inputObjectType(kType, descriptor)
            }

            StructureKind.MAP -> {
                throw GraphixException("Map is not a GraphQL type: $kType")
            }

            is PolymorphicKind -> {
                throw GraphixException("polymorphic types are not GraphQL types yet: $kType")
            }

            else -> {
                throw GraphixException("cannot map ${descriptor.kind} as a GraphQL input type: $kType")
            }
        }
    }

    private fun objectType(
        kType: KType,
        descriptor: SerialDescriptor,
    ): GraphQLOutputType {
        val kClass = kClassOf(kType)
        val name = kClass.graphQLName()
        outputs[name]?.let { return it }
        if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
        val builder =
            GraphQLObjectType
                .newObject()
                .name(name)
                .description(kClass.graphQLDescription())
        properties(kClass, descriptor).forEach { (elementName, elementType, property) ->
            builder.field { field ->
                field
                    .name(property.findAnnotationName() ?: elementName)
                    .description(property.graphQLDescription())
                    .type(output(elementType))
            }
        }
        building.remove(name)
        return builder.build().also { outputs[name] = it }
    }

    private fun inputObjectType(
        kType: KType,
        descriptor: SerialDescriptor,
    ): GraphQLInputType {
        val kClass = kClassOf(kType)
        val name = inputName(kClass)
        inputs[name]?.let { return it }
        if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
        val builder =
            GraphQLInputObjectType
                .newInputObject()
                .name(name)
                .description(kClass.graphQLDescription())
        properties(kClass, descriptor).forEach { (elementName, elementType, property) ->
            val argumentType =
                input(elementType).let { type ->
                    if (constructorOptional(kClass, property.name) && type is GraphQLNonNull) {
                        type.wrappedType as GraphQLInputType
                    } else {
                        type
                    }
                }
            builder.field(
                GraphQLInputObjectField
                    .newInputObjectField()
                    .name(property.findAnnotationName() ?: elementName)
                    .description(property.graphQLDescription())
                    .type(argumentType)
                    .build(),
            )
        }
        building.remove(name)
        return builder.build().also { inputs[name] = it }
    }

    /**
     * GraphQL forbids one name as both object and input object. When [kClass] already mapped
     * as output, the input is `{name}Input`. An explicit `@GraphQLName("…Input")` is left alone.
     */
    private fun inputName(kClass: KClass<*>): String {
        val name = kClass.graphQLName()
        if (name.endsWith("Input")) return name
        if (name in outputs) return name + "Input"
        return name
    }

    private fun enumType(
        kType: KType,
        descriptor: SerialDescriptor,
    ): GraphQLEnumType {
        val kClass = kClassOf(kType)
        val name = kClass.graphQLName()
        enums[name]?.let { return it }
        val builder =
            GraphQLEnumType
                .newEnum()
                .name(name)
                .description(kClass.graphQLDescription())
        descriptor.elementNames.forEach { builder.value(it) }
        return builder.build().also { enums[name] = it }
    }

    /**
     * Serializer for [kType], or [GraphixException] naming the Kotlin type.
     * An inline value class is unwrapped to its underlying descriptor.
     */
    private fun descriptorOf(kType: KType): SerialDescriptor {
        val serializer =
            try {
                serializers.serializer(kType)
            } catch (failure: Exception) {
                val typeName = (kType.classifier as? KClass<*>)?.qualifiedName ?: kType.toString()
                throw GraphixException("$typeName is not @Serializable", failure)
            }
        val descriptor = serializer.descriptor
        return if (descriptor.isInline) descriptor.getElementDescriptor(0) else descriptor
    }

    private fun kClassOf(kType: KType): KClass<*> =
        kType.classifier as? KClass<*>
            ?: throw GraphixException("GraphQL types must be classes, got $kType")

    private fun properties(
        kClass: KClass<*>,
        descriptor: SerialDescriptor,
    ): List<Triple<String, KType, kotlin.reflect.KProperty1<*, *>>> {
        val byName = kClass.memberProperties.associateBy { it.name }
        return (0 until descriptor.elementsCount).mapNotNull { index ->
            val elementName = descriptor.getElementName(index)
            val property =
                byName[elementName]
                    ?: byName.values.find { it.findAnnotationName() == elementName }
                    ?: return@mapNotNull null
            if (property.isGraphQLIgnored()) return@mapNotNull null
            val elementType = property.returnType
            Triple(elementName, elementType, property)
        }
    }

    private fun kotlin.reflect.KProperty<*>.findAnnotationName(): String? = findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }

    /**
     * A Kotlin default on the primary constructor is still a GraphQL `NonNull` unless unwrapped.
     * graphql-java has no constructor defaults.
     */
    private fun constructorOptional(
        kClass: KClass<*>,
        propertyName: String,
    ): Boolean =
        kClass.primaryConstructor
            ?.valueParameters
            ?.find { it.name == propertyName }
            ?.isOptional == true
}
