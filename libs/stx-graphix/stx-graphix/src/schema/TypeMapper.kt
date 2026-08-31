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
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLUnionType
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
    private val extraFields: Map<String, List<TypeFieldMeta>> = emptyMap(),
    private val kotlinScalars: Map<KClass<*>, graphql.schema.GraphQLScalarType> = emptyMap(),
) {
    private val outputs = linkedMapOf<String, GraphQLObjectType>()
    private val inputs = linkedMapOf<String, GraphQLInputObjectType>()
    private val enums = linkedMapOf<String, GraphQLEnumType>()
    private val interfaces = linkedMapOf<String, GraphQLInterfaceType>()
    private val unions = linkedMapOf<String, GraphQLUnionType>()
    private val implementors = linkedMapOf<String, MutableSet<String>>()
    private val building = mutableSetOf<String>()

    /** GraphQL output type for [kType], including nullability. */
    fun output(kType: KType): GraphQLOutputType = wrapOutput(mapOutput(kType), kType.isMarkedNullable)

    /** GraphQL input type for [kType], including nullability. */
    fun input(kType: KType): GraphQLInputType = wrapInput(mapInput(kType), kType.isMarkedNullable)

    /** Every named type this mapper built — for `additionalTypes`. */
    fun additionalTypes(): Set<GraphQLNamedType> =
        (outputs.values + inputs.values + enums.values + interfaces.values + unions.values).toSet()

    /** The interfaces and unions this mapper built. Each needs a type resolver in the code registry. */
    fun abstractTypes(): List<GraphQLNamedType> = interfaces.values + unions.values

    /** Object types implementing [name], for fanning an interface-level mapping onto its implementors. */
    fun implementorsOf(name: String): List<String> = implementors[name].orEmpty().toList()

    private fun mapOutput(kType: KType): GraphQLOutputType {
        scalarFromClass(kType, kotlinScalars)?.let { return it }
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
                abstractType(kType, descriptor.kind as PolymorphicKind)
            }

            else -> {
                throw GraphixException("cannot map ${descriptor.kind} as a GraphQL output type: $kType")
            }
        }
    }

    private fun mapInput(kType: KType): GraphQLInputType {
        scalarFromClass(kType, kotlinScalars)?.let { return it }
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
                throw GraphixException(
                    "sealed $kType cannot be a GraphQL input — GraphQL has no input unions. " +
                        "take a discriminator argument and one input object per case",
                )
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
        val inherited = mutableListOf<TypeFieldMeta>()
        kClass.graphQLInterfaces().forEach { supertype ->
            val interfaceName = (supertype.classifier as KClass<*>).graphQLName()
            builder.withInterface(GraphQLTypeReference.typeRef(interfaceName))
            implementors.getOrPut(interfaceName) { linkedSetOf() } += name
            // An object type must declare every field of the interfaces it implements.
            inherited += extraFields[interfaceName].orEmpty()
            // The interface has to exist in the schema for the reference to resolve.
            if (interfaceName !in interfaces && interfaceName !in building) mapOutput(supertype)
        }
        val propertyNames = mutableSetOf<String>()
        properties(kClass, descriptor).forEach { (elementName, elementType, property) ->
            val fieldName = property.findAnnotationName() ?: elementName
            propertyNames += fieldName
            builder.field { field ->
                field
                    .name(fieldName)
                    .description(property.graphQLDescription())
                    .type(output(elementType))
            }
        }
        val own = extraFields[name].orEmpty()
        (own + inherited.filterNot { extra -> own.any { it.fieldName == extra.fieldName } }).forEach { extra ->
            if (extra.fieldName in propertyNames) {
                throw GraphixException("duplicate field '${extra.fieldName}' on $name")
            }
            propertyNames += extra.fieldName
            builder.field(
                fieldDefinition(
                    extra.function,
                    extra.fieldName,
                    output(extra.graphqlType),
                    this,
                ),
            )
        }
        building.remove(name)
        return builder.build().also { outputs[name] = it }
    }

    /**
     * A sealed hierarchy is a GraphQL `interface` when its subclasses share properties and a
     * `union` when they do not. An open polymorphic type is neither: GraphQL needs a closed set.
     */
    private fun abstractType(
        kType: KType,
        kind: PolymorphicKind,
    ): GraphQLOutputType {
        if (kind != PolymorphicKind.SEALED) {
            throw GraphixException(
                "open polymorphic $kType is not a GraphQL type — " +
                    "GraphQL needs a closed set of possible types; make it a sealed interface",
            )
        }
        val hierarchy = kType.sealedHierarchy()
        return when (hierarchy.shape) {
            SealedShape.INTERFACE -> interfaceType(hierarchy)
            SealedShape.UNION -> unionType(hierarchy)
        }
    }

    private fun interfaceType(hierarchy: SealedHierarchy): GraphQLOutputType {
        val name = hierarchy.name
        interfaces[name]?.let { return it }
        if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
        val builder =
            GraphQLInterfaceType
                .newInterface()
                .name(name)
                .description(hierarchy.base.graphQLDescription())
        hierarchy.sharedProperties.forEach { property ->
            builder.field { field ->
                field
                    .name(property.graphQLPropertyName())
                    .description(property.graphQLDescription())
                    .type(output(property.returnType))
            }
        }
        extraFields[name].orEmpty().forEach { extra ->
            builder.field(fieldDefinition(extra.function, extra.fieldName, output(extra.graphqlType), this))
        }
        building.remove(name)
        val type = builder.build().also { interfaces[name] = it }
        // Members are built after the interface is memoised, so their `implements` reference resolves.
        hierarchy.members.forEach { mapOutput(it) }
        return type
    }

    private fun unionType(hierarchy: SealedHierarchy): GraphQLOutputType {
        val name = hierarchy.name
        unions[name]?.let { return it }
        if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
        val members = hierarchy.members.map { mapOutput(it) }
        val builder =
            GraphQLUnionType
                .newUnionType()
                .name(name)
                .description(hierarchy.base.graphQLDescription())
        members.forEach { member ->
            when (member) {
                is GraphQLObjectType -> builder.possibleType(member)
                is GraphQLTypeReference -> builder.possibleType(member)
                else -> throw GraphixException("union '$name' member is not an object type: $member")
            }
        }
        building.remove(name)
        return builder.build().also { unions[name] = it }
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
            refuseArgumentOnInputField(kClass, property)
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
        if (name in outputs || name in interfaces || name in unions) return name + "Input"
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
     * `@Argument` marks a resolver parameter. The input object is already that argument;
     * its fields are not.
     */
    private fun refuseArgumentOnInputField(
        kClass: KClass<*>,
        property: kotlin.reflect.KProperty<*>,
    ) {
        val parameter = kClass.primaryConstructor?.valueParameters?.find { it.name == property.name }
        val marked =
            parameter?.findAnnotation<Argument>() != null ||
                property.findAnnotation<Argument>() != null
        if (marked) {
            throw GraphixException(
                "${kClass.simpleName}.${property.name} must not be @Argument — " +
                    "the input object is the argument, its fields are not",
            )
        }
    }

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
