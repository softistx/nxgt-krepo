package com.softistx.graphix.execute

import com.softistx.graphix.GraphixException
import com.softistx.graphix.json.toJsonElement
import com.softistx.graphix.schema.GraphQLContext
import com.softistx.graphix.schema.graphQLName
import com.softistx.graphix.schema.isArgument
import com.softistx.graphix.schema.isDataFetchingEnvironment
import graphql.schema.DataFetchingEnvironment
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters

/**
 * Binds GraphQL arguments and `@GraphQLContext` parameters onto [function].
 *
 * An optional Kotlin parameter with no argument is omitted so `callBy` uses the default.
 * `@GraphQLContext` is looked up by `KClass` and is not a GraphQL argument.
 */
internal fun bindArguments(
    function: KFunction<*>,
    environment: DataFetchingEnvironment,
    json: Json,
    skip: Set<KParameter> = emptySet(),
): Map<KParameter, Any?> {
    val bound = LinkedHashMap<KParameter, Any?>()
    function.valueParameters.forEach { parameter ->
        if (parameter in skip) return@forEach
        if (parameter.isDataFetchingEnvironment()) {
            bound[parameter] = environment
            return@forEach
        }
        if (parameter.findAnnotation<GraphQLContext>() != null) {
            bound[parameter] = contextValue(parameter, environment)
            return@forEach
        }
        if (!parameter.isArgument()) return@forEach
        val raw: Any? = environment.getArgument(parameter.graphQLName())
        if (raw == null && parameter.isOptional) return@forEach
        bound[parameter] = decode(raw, parameter, json)
    }
    return bound
}

internal fun contextValue(
    parameter: KParameter,
    environment: DataFetchingEnvironment,
): Any {
    val classifier =
        parameter.type.classifier as? KClass<*>
            ?: throw GraphixException("@GraphQLContext ${parameter.name} needs a class type")
    if (classifier.isSubclassOf(DataFetchingEnvironment::class)) {
        return environment
    }
    return environment.graphQlContext.get<Any>(classifier)
        ?: throw GraphixException("no ${classifier.qualifiedName} in the operation context")
}

/**
 * One GraphQL argument as the Kotlin value the parameter wants.
 *
 * A **scalar** is already that value: its `Coercing` ran before the fetcher, and `BigDecimal`,
 * `java.net.URI` and `java.util.Locale` arrive as themselves. Encoding one back to JSON to decode
 * it again would need a `KSerializer` those types do not have, and would be a round trip for
 * nothing where they do. So a non-generic parameter whose class the value already is passes
 * straight through.
 *
 * Generic types are excluded on purpose: a `List<ProductInput>` **is** a `List` while its elements
 * are still `Map`s, and short-circuiting there would hand the resolver maps.
 */
internal fun decode(
    raw: Any?,
    parameter: KParameter,
    json: Json,
): Any? {
    if (raw == null) return null
    val classifier = parameter.type.classifier as? KClass<*>
    if (parameter.type.arguments.isEmpty() && classifier?.isInstance(raw) == true) return raw
    val serializer = json.serializersModule.serializer(parameter.type)
    val element = raw.toJsonElement()
    return json.decodeFromJsonElement(serializer, element)
}
