package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import kotlinx.coroutines.flow.Flow
import org.reactivestreams.Publisher
import kotlin.reflect.KClass
import kotlin.reflect.KType
import java.util.concurrent.Flow as JdkFlow

/**
 * Element type of a `@Subscription` return. Must be `Flow<T>` or `Publisher<T>` — T is the
 * GraphQL field type.
 */
internal fun KType.subscriptionElement(): KType =
    streamElement()
        ?: throw GraphixException("@Subscription must return Flow<T> or Publisher<T>, got $this")

internal fun KType.streamElement(): KType? {
    val classifier = classifier as? KClass<*> ?: return null
    if (!classifier.isStream()) return null
    return arguments.singleOrNull()?.type
        ?: throw GraphixException("a stream GraphQL type needs an element type: $this")
}

private fun KClass<*>.isStream(): Boolean {
    val java = this.java
    return Flow::class.java.isAssignableFrom(java) ||
        Publisher::class.java.isAssignableFrom(java) ||
        JdkFlow.Publisher::class.java.isAssignableFrom(java)
}
