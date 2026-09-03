package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import kotlinx.coroutines.flow.Flow
import org.reactivestreams.Publisher
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import java.util.concurrent.Flow as JdkFlow

/**
 * Element type of a `@SubscriptionMapping` return. Must be `Flow<T>` or `Publisher<T>` — T is the
 * GraphQL field type.
 */
internal fun KType.subscriptionElement(): KType =
    streamElement()
        ?: throw GraphixException("@SubscriptionMapping must return Flow<T> or Publisher<T>, got $this")

internal fun KType.streamElement(): KType? {
    val classifier = classifier as? KClass<*> ?: return null
    if (!classifier.isStream()) return null
    return arguments.singleOrNull()?.type
        ?: throw GraphixException("a stream GraphQL type needs an element type: $this")
}

private fun KClass<*>.isStream(): Boolean =
    isSubclassOf(Flow::class) || isSubclassOf(Publisher::class) || isSubclassOf(JdkFlow.Publisher::class)

/**
 * The GraphQL type a **non-subscription** return maps to.
 *
 * `CompletionStage<T>` is `T`, as it always was. A `Flow<T>` or a `Publisher<T>` is `List<T>`,
 * because the fetcher collects it before graphql-java ever sees it — sugar for the `.toList()` the
 * resolver would otherwise write, not `@stream`, which graphql-java 26 does not define a directive
 * for at all.
 *
 * Rewriting the type is what keeps streams out of `TypeMapper` altogether: every list rule already
 * written then applies unchanged. That includes the one that would otherwise be wrong — `@GraphQLId`
 * carries down to a list's element by whitelisting `List::class`, and would refuse a `Flow` naming
 * it as a type `ID` cannot be.
 *
 * Not what a `@SubscriptionMapping` does: there a `Flow` is a stream of *separate responses* and
 * [subscriptionElement] unwraps it to `T`. **The annotation is what decides**, which is the one
 * thing about this worth saying loudly.
 */
internal fun KType.resolverOutput(): KType {
    streamElement()?.let { element -> return element.asListType(isMarkedNullable) }
    val awaited = unwrapAsync()
    if (awaited.streamElement() != null) {
        // Refused rather than half-supported: collecting it would mean composing onto a stage from
        // inside a data fetcher whose non-suspend branch must hand a CompletionStage back untouched,
        // and nobody writes this signature in Kotlin — it is a Java-interop shape. Without this the
        // message would come from kotlinx.serialization and point nowhere.
        throw GraphixException("a resolver returns Flow<T> or CompletionStage<T>, not CompletionStage<Flow<T>>: $this")
    }
    return awaited
}

private fun KType.asListType(nullable: Boolean): KType =
    List::class.createType(listOf(KTypeProjection.invariant(this)), nullable = nullable)
