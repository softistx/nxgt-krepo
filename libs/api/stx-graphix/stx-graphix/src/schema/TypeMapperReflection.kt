@file:OptIn(ExperimentalSerializationApi::class)

package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties

/**
 * Serializer for [kType], or [GraphixException] naming the Kotlin type.
 * An inline value class is unwrapped to its underlying descriptor.
 */
internal fun SerializersModule.descriptorOf(kType: KType): SerialDescriptor {
    val serializer =
        try {
            serializer(kType)
        } catch (failure: Exception) {
            val typeName = (kType.classifier as? KClass<*>)?.qualifiedName ?: kType.toString()
            throw GraphixException("$typeName is not @Serializable", failure)
        }
    val descriptor = serializer.descriptor
    return if (descriptor.isInline) descriptor.getElementDescriptor(0) else descriptor
}

internal fun kClassOf(kType: KType): KClass<*> =
    kType.classifier as? KClass<*>
        ?: throw GraphixException("GraphQL types must be classes, got $kType")

internal fun properties(
    kClass: KClass<*>,
    descriptor: SerialDescriptor,
): List<Triple<String, KType, kotlin.reflect.KProperty1<*, *>>> {
    val byName = kClass.memberProperties.associateBy { it.name }
    return (0 until descriptor.elementsCount).mapNotNull { index ->
        val elementName = descriptor.getElementName(index)
        // The element name is the serial name, which is the Kotlin name only when nothing
        // renamed it. A property found by neither is one this descriptor does not describe.
        val property =
            byName[elementName]
                ?: byName.values.find { it.graphQLPropertyName() == elementName }
                ?: return@mapNotNull null
        if (property.isGraphQLIgnored()) return@mapNotNull null
        val elementType = property.returnType
        Triple(elementName, elementType, property)
    }
}
