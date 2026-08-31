package com.strange.graphix

import com.strange.common.serialization.lenientJson
import com.strange.graphix.execute.RegisteredLoader
import com.strange.graphix.execute.executionInput
import com.strange.graphix.execute.toGraphixResult
import com.strange.graphix.schema.DefaultSchemaExtensions
import com.strange.graphix.schema.DefaultSchemaLocations
import com.strange.graphix.schema.FieldDirectiveWrap
import com.strange.graphix.schema.GraphixDirective
import com.strange.graphix.schema.GraphixTypeName
import com.strange.graphix.schema.graphQLSchema
import com.strange.graphix.schema.loadSchemaFiles
import com.strange.graphix.validation.GraphixValidation
import com.strange.graphix.validation.GraphixValidationBuilder
import graphql.GraphQL
import graphql.execution.instrumentation.fieldvalidation.FieldValidationInstrumentation
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.reactivestreams.Publisher
import kotlin.reflect.KClass

/**
 * A GraphQL engine built from annotated Kotlin functions and `@Serializable` types.
 *
 * The application **names** its roots. There is no classpath scan for resolver classes — Spring
 * may collect `@GraphQLController` beans; that is `stx-graphix-spring`. Schema documents under
 * `classpath:graphql/` are scanned, Spring GraphQL's default. A data fetcher is not part of this
 * API: each `@QueryMapping` / `@MutationMapping` / `@SubscriptionMapping` / `@SchemaMapping` is a function on the instance
 * passed to [GraphixBuilder.query], [GraphixBuilder.mutation], [GraphixBuilder.subscription]
 * or [GraphixBuilder.type], so a Spring `OrderService` lives on that instance's constructor,
 * not in [execute]'s context map.
 *
 * ```kotlin
 * val graphix = Graphix {
 *     query(ProductQueries(store))
 *     mutation(ProductMutations(store))
 *     subscription(ProductSubscriptions(store))
 *     type(ProductFields(reviews))
 * }
 * val result = graphix.execute(GraphixRequest("{ products { name } }"))
 * graphix.subscribe(GraphixRequest("subscription { productAdded { name } }"))
 * ```
 */
class Graphix internal constructor(
    internal val engine: GraphQL,
    internal val loaders: List<RegisteredLoader> = emptyList(),
    internal val validation: GraphixValidation? = null,
) {
    /**
     * Runs one query or mutation. Field failures land in [GraphixResult.errors]; this call
     * still returns. A document that cannot even be submitted throws [GraphixException]. A
     * schema that could not be built already threw from [Graphix], not from here.
     *
     * A **subscription** is [subscribe] — graphql-java's result is a `Publisher`, which this
     * method refuses rather than serialising as a single JSON object.
     *
     * [context] is the per-operation bag, keyed by `KClass`. A resolver parameter annotated
     * `@GraphQLContext` is looked up there. A `CoroutineScope` is installed as well so `suspend`
     * resolvers run; it is cancelled when this returns. HTTP plugins do not yet put the call
     * or the security principal in [context] — whoever owns the HTTP request must, until they do.
     *
     * @param request the GraphQL document and already-decoded variables
     * @param context per-operation values for `@GraphQLContext` parameters, not Spring beans
     */
    suspend fun execute(
        request: GraphixRequest,
        context: Map<KClass<*>, Any> = emptyMap(),
    ): GraphixResult {
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val scope = CoroutineScope(currentCoroutineContext() + job + CoroutineName("graphql"))
        return try {
            val result =
                engine.executeAsync(executionInput(request, context, scope, loaders, validation)).await()
            if (result.getData<Any>() is Publisher<*>) {
                throw GraphixException("this is a subscription — use Graphix.subscribe")
            }
            result.toGraphixResult()
        } finally {
            job.cancel()
        }
    }

    /** The schema as GraphQL SDL, for introspection dumps and tests. */
    fun sdl(): String = SchemaPrinter().print(engine.graphQLSchema)
}

/**
 * Accumulates query, mutation, subscription and type-field **instances**. Each instance is
 * kept for the life of the engine: the data fetcher calls methods on it, it does not
 * construct a new one per request.
 */
class GraphixBuilder internal constructor(
    private val json: Json,
) {
    private val queries = mutableListOf<Any>()
    private val mutations = mutableListOf<Any>()
    private val subscriptions = mutableListOf<Any>()
    private val types = mutableListOf<Any>()
    private var resourceLocations: List<String> = DefaultSchemaLocations
    private var resourceExtensions: List<String> = DefaultSchemaExtensions
    private val customScalars = mutableListOf<GraphQLScalarType>()
    private val kotlinScalars = mutableMapOf<KClass<*>, GraphQLScalarType>()
    private val fieldDirectives = mutableMapOf<String, FieldDirectiveWrap>()
    private val typeResolvers = mutableMapOf<String, GraphixTypeName>()
    private val engineCustomizers = mutableListOf<GraphQLEngineCustomizer>()
    private var validation: GraphixValidation? = null

    /** Registers [instance]; every `@QueryMapping` function on it becomes a field on `Query`. */
    fun query(instance: Any) {
        queries += instance
    }

    /** Registers [instance]; every `@MutationMapping` function on it becomes a field on `Mutation`. */
    fun mutation(instance: Any) {
        mutations += instance
    }

    /** Registers [instance]; every `@SubscriptionMapping` function on it becomes a field on `Subscription`. */
    fun subscription(instance: Any) {
        subscriptions += instance
    }

    /**
     * Registers [instance]; every `@SchemaMapping` / `@BatchMapping` function becomes an extra
     * field on its parent type. Nested `@Serializable` properties stay property getters.
     * A field is one or the other, not both.
     */
    fun type(instance: Any) {
        types += instance
    }

    /**
     * Directories of schema documents, Spring GraphQL's `classpath:graphql/` among them.
     * Every `.graphqls` / `.gqls` under the directory is merged (`extend type` works).
     * An empty scan keeps the annotation-derived schema.
     */
    fun schemaLocations(vararg locations: String) {
        schemaLocations(locations.asList())
    }

    fun schemaLocations(locations: Collection<String>) {
        resourceLocations = locations.toList()
    }

    /** File suffixes scanned under [schemaLocations]. Default `.graphqls` and `.gqls`. */
    fun schemaFileExtensions(vararg extensions: String) {
        schemaFileExtensions(extensions.asList())
    }

    fun schemaFileExtensions(extensions: Collection<String>) {
        resourceExtensions = extensions.toList()
    }

    internal fun addScalar(
        type: GraphQLScalarType,
        kotlinType: KClass<*>?,
    ) {
        if (customScalars.any { it.name == type.name }) {
            throw GraphixException("duplicate scalar '${type.name}'")
        }
        customScalars += type
        if (kotlinType != null) kotlinScalars[kotlinType] = type
    }

    internal fun addFieldDirective(directive: GraphixDirective) {
        if (!fieldDirectives.containsKey(directive.name)) {
            fieldDirectives[directive.name] = directive.wrap
            return
        }
        throw GraphixException("duplicate field directive '${directive.name}'")
    }

    internal fun addTypeResolver(
        typeName: String,
        resolver: GraphixTypeName,
    ) {
        if (typeResolvers.putIfAbsent(typeName, resolver) != null) {
            throw GraphixException("duplicate type resolver for '$typeName'")
        }
    }

    internal fun addEngineCustomizer(customizer: GraphQLEngineCustomizer) {
        engineCustomizers += customizer
    }

    /**
     * graphql-java 26 validation: complexity limits in the operation context, field rules as
     * instrumentation. Declared here so a caller does not wire `QueryComplexityLimits` or
     * `FieldValidationInstrumentation` by hand.
     *
     * Limits land in `GraphQLContext` next to the operation [CoroutineScope] — they are per
     * operation, not a process-wide default. Field rules run before execution, on already-coerced
     * arguments, and must not suspend (graphql-java's hook is not a coroutine).
     */
    fun validation(block: GraphixValidationBuilder.() -> Unit) {
        validation = GraphixValidationBuilder().apply(block).build()
    }

    internal fun build(): Graphix {
        val files = resourceLocations.loadSchemaFiles(resourceExtensions)
        val (schema, loaders) =
            graphQLSchema(
                queries,
                mutations,
                subscriptions,
                types,
                json,
                files,
                customScalars,
                kotlinScalars,
                fieldDirectives,
                typeResolvers,
            )
        val builder = GraphQL.newGraphQL(schema)
        validation?.fieldValidation()?.let { builder.instrumentation(FieldValidationInstrumentation(it)) }
        engineCustomizers.forEach { with(it) { builder.customize() } }
        return Graphix(builder.build(), loaders, validation)
    }
}

/**
 * Builds a [Graphix] from named roots. [json] is how arguments and `@Serializable` types are
 * read; the default is `stx-common`'s lenient `Json`.
 *
 * [GraphixBuilder.subscription] and [GraphixBuilder.type] are optional. GraphQL still
 * requires a query root.
 */
fun Graphix(
    json: Json = lenientJson,
    block: GraphixBuilder.() -> Unit,
): Graphix = GraphixBuilder(json).apply(block).build()
