package com.strange.graphix.schema

import graphql.TypeResolutionEnvironment
import graphql.execution.UnresolvedTypeException
import graphql.schema.GraphQLNamedOutputType
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Which object type a runtime value is, for every GraphQL interface and union.
 *
 * The value **is** the Kotlin instance — nothing round-trips through kotlinx.serialization on the
 * way out, so the discriminator the serializer would have written is not there to read. The name
 * is therefore the class's: [GraphQLName] if present, otherwise the Kotlin simple name. That is
 * the same convention every other type in this library follows, which is why an SDL `union` or
 * `interface` needs no wiring at all.
 *
 * A value it cannot place raises [UnresolvedTypeException] — graphql-java turns that into a
 * GraphQL error on the field. Anything else thrown here would escape
 * `ExecutionStrategy`'s catch and kill the whole operation.
 */
internal class GraphixTypeResolver(
    private val overrides: Map<String, GraphixTypeName> = emptyMap(),
) : TypeResolver {
    override fun getType(environment: TypeResolutionEnvironment): GraphQLObjectType {
        val abstract = environment.fieldType as GraphQLNamedOutputType
        val value: Any? = environment.getObject<Any>()
        val name =
            overrides[abstract.name]?.let { with(it) { environment.resolve(value) } }
                ?: runtimeTypeName(value)
        if (name.isNullOrBlank()) {
            throw UnresolvedTypeException(
                "Graphix cannot resolve '${abstract.name}' from ${describe(value)} — " +
                    "it has no GraphQL name",
                abstract,
            )
        }
        // getObjectType asserts and would escape the engine's catch; getType lets us word the error.
        return environment.schema.getType(name) as? GraphQLObjectType
            ?: throw UnresolvedTypeException(
                "Graphix cannot resolve '${abstract.name}': '$name' is not an object type in the schema",
                abstract,
            )
    }
}

/** The GraphQL type name a runtime value claims: `__typename` on a map, else the class's name. */
internal fun runtimeTypeName(value: Any?): String? =
    when (value) {
        null -> null
        is Map<*, *> -> value["__typename"] as? String
        else -> typeNames.computeIfAbsent(value::class) { it.graphQLNameOrNull() ?: "" }
    }

/** Per-value reflection is not free — an abstract-typed list would pay it per row. */
private val typeNames = ConcurrentHashMap<KClass<*>, String>()

private fun describe(value: Any?): String = value?.let { it::class.qualifiedName ?: it::class.toString() } ?: "null"
