package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import com.softistx.graphix.scalar.Scalars
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLUnionType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.uuid.ExperimentalUuidApi

/** The built-ins, as a set, so [TypeMapper.record] is a lookup and not a scan. */
private val BuiltInScalarTypes: Set<graphql.schema.GraphQLScalarType> = Scalars.All.toSet()

/** A GraphQL field on [typeName] named [fieldName], backed by the Kotlin property [propertyName]. */
internal data class PropertyRename(
    val typeName: String,
    val fieldName: String,
    val propertyName: String,
)

/**
 * SerialDescriptor is the type system. A Kotlin type that has no serializer is a schema-build
 * failure naming that type, not a silent Map.
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)
internal class TypeMapper(
    val serializers: SerializersModule,
    val extraFields: Map<String, List<TypeFieldMeta>> = emptyMap(),
    private val kotlinScalars: Map<KClass<*>, graphql.schema.GraphQLScalarType> = emptyMap(),
) {
    internal val outputs = linkedMapOf<String, GraphQLObjectType>()
    internal val inputs = linkedMapOf<String, GraphQLInputObjectType>()
    internal val enums = linkedMapOf<String, GraphQLEnumType>()
    internal val interfaces = linkedMapOf<String, GraphQLInterfaceType>()
    internal val unions = linkedMapOf<String, GraphQLUnionType>()
    internal val implementors = linkedMapOf<String, MutableSet<String>>()
    internal val renames = mutableListOf<PropertyRename>()
    internal val building = mutableSetOf<String>()
    private val scalars = linkedSetOf<graphql.schema.GraphQLScalarType>()

    /** GraphQL output type for [kType], including nullability. [id] makes a string type `ID`. */
    fun output(
        kType: KType,
        id: Boolean = false,
    ): GraphQLOutputType = wrapOutput(mapOutput(kType, id), kType.isMarkedNullable)

    /** GraphQL input type for [kType], including nullability. [id] makes a string type `ID`. */
    fun input(
        kType: KType,
        id: Boolean = false,
    ): GraphQLInputType = wrapInput(mapInput(kType, id), kType.isMarkedNullable)

    /**
     * Every named type this mapper built — for `additionalTypes`. The scalars in it are the
     * built-in ones a field actually used: a service with no dates does not advertise
     * `scalar Instant`, and a schema that advertised all two dozen would say nothing at all.
     */
    fun additionalTypes(): Set<GraphQLNamedType> =
        (outputs.values + inputs.values + enums.values + interfaces.values + unions.values + scalars).toSet()

    /** Notes a built-in scalar as used. Spec scalars are always in the schema and are not noted. */
    private fun record(type: graphql.schema.GraphQLScalarType): graphql.schema.GraphQLScalarType =
        type.also { if (it in BuiltInScalarTypes) scalars += it }

    /** The interfaces and unions this mapper built. Each needs a type resolver in the code registry. */
    fun abstractTypes(): List<GraphQLNamedType> = interfaces.values + unions.values

    /** Object types implementing [name], for fanning an interface-level mapping onto its implementors. */
    fun implementorsOf(name: String): List<String> = implementors[name].orEmpty().toList()

    /** Fields whose GraphQL name is not the Kotlin one. Each needs a fetcher that knows both. */
    fun propertyRenames(): List<PropertyRename> = renames.toList()

    internal fun mapOutput(
        kType: KType,
        id: Boolean = false,
    ): GraphQLOutputType {
        if (id) idScalar(kType)?.let { return it }
        scalarFromClass(kType, kotlinScalars)?.let { return record(it) }
        val descriptor = serializers.descriptorOf(kType)
        scalarOf(descriptor)?.let { return record(it) }
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                GraphQLList.list(
                    output(
                        kType.arguments.single().type
                            ?: throw GraphixException("a list GraphQL type needs an element type: $kType"),
                        id,
                    ),
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

    private fun mapInput(
        kType: KType,
        id: Boolean = false,
    ): GraphQLInputType {
        if (id) idScalar(kType)?.let { return it }
        scalarFromClass(kType, kotlinScalars)?.let { return record(it) }
        val descriptor = serializers.descriptorOf(kType)
        scalarOf(descriptor)?.let { return record(it) }
        return when (descriptor.kind) {
            StructureKind.LIST -> {
                GraphQLList.list(
                    input(
                        kType.arguments.single().type
                            ?: throw GraphixException("a list GraphQL type needs an element type: $kType"),
                        id,
                    ),
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

}
