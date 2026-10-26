package com.softistx.graphix

import com.softistx.graphix.error.ErrorHandlers
import com.softistx.graphix.error.GraphixExceptionHandler
import com.softistx.graphix.execute.errorDispatch
import com.softistx.graphix.http.GraphqlWsInit
import com.softistx.graphix.intercept.GraphixInterceptor
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

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
    private var maxListElements: Int? = null
    private val engineCustomizers = mutableListOf<GraphQLEngineCustomizer>()

    // Whoever fills the operation context registers the type. The core fills exactly one:
    // GraphqlWsSession puts the client's `connection_init` payload on every graphql-ws operation.
    private val contextTypes = mutableSetOf<KClass<*>>(GraphqlWsInit::class)
    private val interceptors = mutableListOf<GraphixInterceptor>()
    private var validation: GraphixValidation? = null
    private var messages: GraphixMessages = GraphixMessages.Bundled

    // Ordered: the first handler to answer wins, the way the first interceptor to run is outermost.
    private val errorHandlers = mutableListOf<GraphixExceptionHandler>()
    private var errorFallback: GraphixExceptionHandler? = null

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

    /**
     * How many elements a `Flow` field may produce before it fails.
     *
     * Unset, and nothing is bounded — a `List` return has always been unbounded and this changes
     * none of it. It exists because a `Flow` can be *infinite* where a `List` cannot: without a
     * bound such a field does not fail, it simply never answers, and a request that never answers is
     * worse than one that errors. Past it the field is a [TooManyElements] an `errors { }` handler
     * can edit like any other.
     *
     * It bounds emission, not silence. A flow that emits nothing and never completes still hangs
     * until the operation is cancelled — that wants a request timeout, which is a different thing.
     */
    fun maxListElements(max: Int) {
        require(max > 0) { "maxListElements must be positive" }
        maxListElements = max
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

    internal fun addErrorHandler(handler: GraphixExceptionHandler) {
        errorHandlers += handler
    }

    internal fun addErrorFallback(handler: GraphixExceptionHandler) {
        if (errorFallback != null) throw GraphixException("the error fallback is already set")
        errorFallback = handler
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
        val handlers = ErrorHandlers(errorHandlers.toList(), errorFallback)
        // Before the customizers, not after: `engine { defaultDataFetcherExceptionHandler(…) }` is an
        // explicit choice and should still win. What it cannot survive is
        // `engine { queryExecutionStrategy(…) }` — graphql-java only applies the default handler to
        // strategies left null at build, so setting one silently drops this seat. Documented, not
        // guessable from the API.
        if (!handlers.isEmpty()) builder.defaultDataFetcherExceptionHandler(errorDispatch(handlers, messages))
        engineCustomizers.forEach { with(it) { builder.customize() } }
        return Graphix(
            builder.build(),
            loaders,
            validation,
            introspection,
            messages,
            interceptors.toList(),
            handlers,
            maxListElements,
        )
    }
}
