package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

/**
 * What a Kotlin `sealed` hierarchy means in GraphQL. Pure reflection — no graphql-java here, and
 * no memo state: [TypeMapper] owns the building, this file owns the rules.
 */
internal enum class SealedShape { INTERFACE, UNION }

/** A sealed hierarchy, resolved to the GraphQL abstract type it becomes. */
internal class SealedHierarchy(
    val base: KClass<*>,
    val name: String,
    val shape: SealedShape,
    /** One [KType] per leaf subclass, in declaration order. */
    val members: List<KType>,
    /** Fields the GraphQL interface declares. Empty when [shape] is [SealedShape.UNION]. */
    val sharedProperties: List<KProperty1<*, *>>,
)

/**
 * Resolves [this] sealed type. Throws [GraphixException] naming the Kotlin type when it cannot be
 * a GraphQL abstract type.
 */
internal fun KType.sealedHierarchy(): SealedHierarchy {
    val base =
        classifier as? KClass<*>
            ?: throw GraphixException("GraphQL types must be classes, got $this")
    if (!base.isSealed) {
        throw GraphixException(
            "${base.qualifiedName} is polymorphic but not sealed — " +
                "GraphQL needs a closed set of possible types",
        )
    }
    if (base.typeParameters.isNotEmpty()) {
        throw GraphixException("generic sealed type ${base.qualifiedName} is not a GraphQL type")
    }
    val leaves = base.sealedLeaves()
    if (leaves.isEmpty()) {
        throw GraphixException(
            "sealed ${base.simpleName} has no concrete subclasses — " +
                "a GraphQL interface or union needs at least one member type",
        )
    }
    val shape = base.sealedShape()
    val shared = if (shape == SealedShape.INTERFACE) base.sharedGraphQLProperties() else emptyList()
    val name = base.graphQLName()
    leaves.forEach { leaf ->
        leaf.refuseEmpty(name)
        if (shape == SealedShape.INTERFACE) leaf.refuseRenamed(base, name)
    }
    return SealedHierarchy(
        base = base,
        name = name,
        shape = shape,
        members = leaves.map { it.createType() },
        sharedProperties = shared,
    )
}

/**
 * Concrete subclasses, recursing through nested sealed levels. `sealedSubclasses` is direct-only,
 * so a `sealed interface Container : Node` would otherwise reach the schema as a member type.
 */
internal fun KClass<*>.sealedLeaves(): List<KClass<*>> =
    sealedSubclasses.flatMap { subclass ->
        if (subclass.isSealed) subclass.sealedLeaves() else listOf(subclass)
    }

/**
 * A sealed type that declares properties every subclass carries is a GraphQL `interface`; one that
 * declares none can only be a `union`, since a GraphQL interface needs at least one field.
 * [GraphQLUnion] forces the union direction.
 */
internal fun KClass<*>.sealedShape(): SealedShape =
    when {
        hasAnnotation<GraphQLUnion>() -> SealedShape.UNION
        sharedGraphQLProperties().isNotEmpty() -> SealedShape.INTERFACE
        else -> SealedShape.UNION
    }

/** Properties the sealed type declares, minus the ignored ones, in a stable order. */
internal fun KClass<*>.sharedGraphQLProperties(): List<KProperty1<*, *>> =
    memberProperties.filterNot { it.isGraphQLIgnored() }.sortedBy { it.graphQLPropertyName() }

/**
 * Sealed `@Serializable` supertypes of [this] that become GraphQL interfaces — what the type
 * declares `implements` for. A sealed supertype that is not `@Serializable` is Kotlin structure,
 * not a GraphQL type, and is left alone.
 *
 * The walk is **transitive**, because GraphQL's is not: an object reached through an intermediate
 * sealed level (`Boarding : Paper : Ticketed`) must declare every interface in the chain, or it is
 * not a possible type of the outermost one and resolving it fails at execute time.
 */
internal fun KClass<*>.graphQLInterfaces(): List<KType> {
    val found = linkedMapOf<KClass<*>, KType>()

    fun walk(kClass: KClass<*>) {
        kClass.supertypes.forEach { supertype ->
            val classifier = supertype.classifier as? KClass<*> ?: return@forEach
            if (classifier in found) return@forEach
            if (!classifier.isSealed || !classifier.hasAnnotation<Serializable>() || classifier.typeParameters.isNotEmpty()) {
                return@forEach
            }
            if (classifier.sealedShape() == SealedShape.INTERFACE) found[classifier] = supertype
            // A union level is not a GraphQL type of its own, but what is above it may be.
            walk(classifier)
        }
    }
    walk(this)
    return found.values.toList()
}

/** A GraphQL object type needs at least one field, so a `data object` member is a build failure. */
private fun KClass<*>.refuseEmpty(abstractName: String) {
    if (memberProperties.none { !it.isGraphQLIgnored() }) {
        throw GraphixException(
            "$simpleName is a member of '$abstractName' but has no GraphQL fields — " +
                "a GraphQL object type needs at least one",
        )
    }
}

/**
 * An implementor may not name a field differently from the interface that declares it: GraphQL
 * matches on the name, and neither `@GraphQLName` nor `@SerialName` is inherited by an override.
 */
private fun KClass<*>.refuseRenamed(
    base: KClass<*>,
    abstractName: String,
) {
    val declared = base.sharedGraphQLProperties().associate { it.name to it.graphQLPropertyName() }
    memberProperties.forEach { property ->
        val expected = declared[property.name] ?: return@forEach
        val actual = property.graphQLPropertyName()
        if (actual != expected) {
            throw GraphixException(
                "$simpleName.${property.name} is '$actual' but interface '$abstractName' declares it as " +
                    "'$expected' — an implementor must keep the interface's field name",
            )
        }
    }
}
