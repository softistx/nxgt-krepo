package com.softistx.graphix

import com.softistx.common.serialization.lenientJson
import com.softistx.graphix.error.ErrorHandlers
import com.softistx.graphix.error.ErrorHandling
import com.softistx.graphix.error.GraphixExceptionHandler
import com.softistx.graphix.error.handlerFunctions
import com.softistx.graphix.error.handling
import com.softistx.graphix.execute.RegisteredLoader
import com.softistx.graphix.execute.errorDispatch
import com.softistx.graphix.execute.executionInput
import com.softistx.graphix.execute.toGraphixResult
import com.softistx.graphix.http.GraphqlWsInit
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.runChain
import com.softistx.graphix.message.GraphixMessages
import com.softistx.graphix.schema.DefaultSchemaExtensions
import com.softistx.graphix.schema.DefaultSchemaLocations
import com.softistx.graphix.schema.FieldDirectiveWrap
import com.softistx.graphix.schema.GraphixDirective
import com.softistx.graphix.schema.GraphixTypeName
import com.softistx.graphix.schema.carriesMappings
import com.softistx.graphix.schema.graphQLSchema
import com.softistx.graphix.schema.loadSchemaFiles
import com.softistx.graphix.validation.GraphixValidation
import com.softistx.graphix.validation.GraphixValidationBuilder
import graphql.GraphQL
import graphql.execution.instrumentation.fieldvalidation.FieldValidationInstrumentation
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
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
 *     resolvers(
 *         ProductQueries(store),
 *         ProductMutations(store),
 *         ProductSubscriptions(store),
 *         ProductFields(reviews),
 *     )
 * }
 * val result = graphix.execute(GraphixRequest("{ products { name } }"))
 * graphix.subscribe(GraphixRequest("subscription { productAdded { name } }"))
 * ```
 */
class Graphix internal constructor(
    internal val engine: GraphQL,
    internal val loaders: List<RegisteredLoader> = emptyList(),
    internal val validation: GraphixValidation? = null,
    internal val introspection: Boolean = true,
    internal val messages: GraphixMessages = GraphixMessages.Bundled,
    internal val interceptors: List<GraphixInterceptor> = emptyList(),
    /** Kept past `build()` because the seats outside graphql-java need it too. */
    internal val errorHandlers: ErrorHandlers = ErrorHandlers(emptyMap(), null),
) {
    /**
     * Runs one query or mutation. Field failures land in [GraphixResult.errors]; this call
     * still returns. A document that cannot even be submitted throws [GraphixException]. A
     * schema that could not be built already threw from [Graphix], not from here.
     *
     * A **subscription** is [subscribe] — graphql-java's result is a `Publisher`, which this
     * method refuses rather than serialising as a single JSON object.
     *
     * [context] is the per-operation bag, keyed by `KClass`, and it is what a resolver's
     * framework parameters are read from. A `CoroutineScope`
     * is installed alongside it so `suspend` resolvers run; it is cancelled when this returns.
     * An HTTP integration puts its call there — `ApplicationCall`, `ServerWebExchange` — and
     * [GraphixInterceptor] is how an application adds to it without owning the call site.
     *
     * @param request the GraphQL document and already-decoded variables
     * @param context per-operation values, not Spring beans
     */
    suspend fun execute(
        request: GraphixRequest,
        context: Map<KClass<*>, Any> = emptyMap(),
    ): GraphixResult =
        runChain(interceptors, request, context) { operation, values ->
            flow { emit(executeOnce(operation, values)) }
        }.single()

    private suspend fun executeOnce(
        request: GraphixRequest,
        context: Map<KClass<*>, Any>,
    ): GraphixResult {
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val scope = CoroutineScope(currentCoroutineContext() + job + CoroutineName("graphql"))
        return try {
            val result =
                engine
                    .executeAsync(executionInput(request, context, scope, loaders, validation, introspection, messages))
                    .await()
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
    private val instances = mutableListOf<Any>()
    private var resourceLocations: List<String> = DefaultSchemaLocations
    private var resourceExtensions: List<String> = DefaultSchemaExtensions
    private val customScalars = mutableListOf<GraphQLScalarType>()
    private val kotlinScalars = mutableMapOf<KClass<*>, GraphQLScalarType>()
    private val fieldDirectives = mutableMapOf<String, FieldDirectiveWrap>()
    private val typeResolvers = mutableMapOf<String, GraphixTypeName>()
    private var introspection = true
    private var builtInScalars = true
    private val engineCustomizers = mutableListOf<GraphQLEngineCustomizer>()

    // Whoever fills the operation context registers the type. The core fills exactly one:
    // GraphqlWsSession puts the client's `connection_init` payload on every graphql-ws operation.
    private val contextTypes = mutableSetOf<KClass<*>>(GraphqlWsInit::class)
    private val interceptors = mutableListOf<GraphixInterceptor>()
    private var validation: GraphixValidation? = null
    private var messages: GraphixMessages = GraphixMessages.Bundled

    // Insertion-ordered, but order is not what picks a handler: the thrown class's own hierarchy is.
    // The map exists so a second registration for one exception type is refused rather than shadowed.
    private val errorHandlers = LinkedHashMap<KClass<out Throwable>, ErrorHandling>()
    private var errorFallback: ErrorHandling? = null

    /**
     * Registers [instances]. Each one's annotated functions say what they are: `@QueryMapping`
     * becomes a field on `Query`, `@MutationMapping` on `Mutation`, `@SubscriptionMapping` on
     * `Subscription`, and `@SchemaMapping` / `@BatchMapping` an extra field on the parent type
     * they name. A `dataLoader { }` property on any of them is registered too.
     *
     * ```kotlin
     * Graphix {
     *     resolvers(ProductQueries(store), ProductMutations(store), ProductFields(reviews))
     * }
     * ```
     *
     * There is **one** call because the annotation already discriminates. Asking for `query(…)`
     * as well was asking the caller to repeat what the function had already declared, and it made
     * a class holding both a query and a mutation something you had to register twice. A class
     * carrying no mapping at all is a build failure naming that class — which is the mistake the
     * old `query(NotAQuery())` used to catch.
     *
     * The application still **names** what it registers: this is a list of constructed objects,
     * nothing is scanned for, and a Spring `OrderService` lives on one of their constructors.
     */
    fun resolvers(vararg instances: Any) {
        resolvers(instances.asList())
    }

    fun resolvers(instances: Collection<Any>) {
        instances.forEach { instance ->
            if (!instance.carriesMappings()) {
                throw GraphixException(
                    "${instance::class.qualifiedName} has no @QueryMapping, @MutationMapping, " +
                        "@SubscriptionMapping, @SchemaMapping or @BatchMapping function",
                )
            }
            this.instances += instance
        }
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

    /**
     * Whether every built-in scalar is in the schema. **On by default**: `LocalDate`, `BigDecimal`,
     * `PositiveInt` and the rest are there whether or not a field uses one, so a client's code
     * generator sees the whole vocabulary and a bounded scalar needs no registration.
     *
     * Off, the schema carries only the built-ins a field actually resolved to, and a bounded one
     * is reached by `scalars(...)` or by declaring it in SDL. That is the choice to make when
     * introspection is a published contract and its size is part of the contract.
     *
     * A scalar the application defined under a built-in's name is unaffected either way: its own
     * definition wins.
     */
    fun builtInScalars(enabled: Boolean) {
        builtInScalars = enabled
    }

    /**
     * Whether `__schema` and `__type` answer. On by default — GraphiQL and Apollo Sandbox need
     * them. Turned off, an operation selecting either comes back with one GraphQL error and no
     * data; every other operation is unaffected, and the schema itself is unchanged.
     */
    fun introspection(enabled: Boolean) {
        introspection = enabled
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

    internal fun addContextParameter(type: KClass<*>) {
        contextTypes += type
    }

    internal fun addInterceptor(interceptor: GraphixInterceptor) {
        interceptors += interceptor
    }

    internal fun addErrorHandler(
        type: KClass<out Throwable>,
        handler: ErrorHandling,
    ) {
        if (errorHandlers.putIfAbsent(type, handler) != null) {
            throw GraphixException("two handlers for ${type.qualifiedName} — one exception type, one answer")
        }
    }

    internal fun addErrorFallback(handler: ErrorHandling) {
        if (errorFallback != null) throw GraphixException("the error fallback is already set")
        errorFallback = handler
    }

    /**
     * Reflects [handler]'s `@ExceptionMapping` functions now, so a parameter it cannot fill is a
     * schema-build failure rather than a second failure on the day the first one happens.
     */
    internal fun addExceptionHandler(handler: GraphixExceptionHandler) {
        val functions = handler.handlerFunctions(contextTypes)
        if (functions.isEmpty()) {
            throw GraphixException("${handler::class.qualifiedName} has no @ExceptionMapping function")
        }
        functions.forEach { addErrorHandler(it.exceptionType, it.handling()) }
    }

    /**
     * Where coercion errors get their text. The default is the catalogues in this jar, English and
     * French; anything else is one lambda, and `stx-i18n` fits it directly:
     *
     * ```kotlin
     * messages { locale, key, args -> catalog.forLocale(locale).translate(key, args) }
     * ```
     *
     * The locale is the operation's — `GraphixRequest.locale`, which the HTTP integrations
     * negotiate from `Accept-Language`. A single operation may override the source by putting a
     * [GraphixMessages] in `execute`'s context map.
     *
     * **One place this does not reach**, and it is graphql-java's: a **literal written in the
     * document** is coerced during validation, and `ValidationContext` builds a `GraphQLContext`
     * of its own holding the locale and nothing else, so the source put there for the operation
     * is not there to be found. Those errors come from the bundled catalogue — in the right
     * language, since the locale does survive. A variable's value and a resolver's result are
     * coerced during execution and see this source. `ScalarMessageTest` pins both halves.
     *
     * Adding a **language** has no such split: the bundled catalogue reads
     * `stx/graphix/messages_<locale>.properties` off the classpath, so a file in the application's own
     * resources answers in both phases.
     */
    fun messages(source: GraphixMessages) {
        messages = source
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
                instances,
                json,
                files,
                customScalars,
                kotlinScalars,
                fieldDirectives,
                typeResolvers,
                builtInScalars,
                contextTypes,
            )
        val builder = GraphQL.newGraphQL(schema)
        validation?.fieldValidation()?.let { builder.instrumentation(FieldValidationInstrumentation(it)) }
        val handlers = ErrorHandlers(errorHandlers.toMap(), errorFallback)
        // Before the customizers, not after: `engine { defaultDataFetcherExceptionHandler(…) }` is an
        // explicit choice and should still win. What it cannot survive is
        // `engine { queryExecutionStrategy(…) }` — graphql-java only applies the default handler to
        // strategies left null at build, so setting one silently drops this seat. Documented, not
        // guessable from the API.
        if (!handlers.isEmpty()) builder.defaultDataFetcherExceptionHandler(errorDispatch(handlers, messages))
        engineCustomizers.forEach { with(it) { builder.customize() } }
        return Graphix(builder.build(), loaders, validation, introspection, messages, interceptors.toList(), handlers)
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
