package com.softistx.graphix.koin

/**
 * Marks a class as a Graphix resolver so [fromKoin] can find it.
 *
 * ```kotlin
 * @Singleton
 * class ProductQueries(private val store: Store) : GraphixResolver {
 *     @QueryMapping
 *     suspend fun products(): List<Product> = store.all()
 * }
 * ```
 *
 * **Why an interface and not an annotation.** Spring finds its controllers with
 * `getBeansWithAnnotation<GraphQLController>()`; Koin has no equivalent — a container that resolves
 * by type can only be asked for a type. `Koin.getAll<T>()` does enumerate every single bound to `T`,
 * and koin-annotations binds a class to the interfaces it implements, so implementing this is what
 * makes a resolver findable.
 *
 * A meta-annotation would have been nicer — `@Singleton annotation class GraphQLController`, one
 * mark instead of two. It does not work: Koin's compiler plugin matches direct annotations only, so
 * a meta-annotated class compiles and is then **silently absent** from the container. Measured on
 * Koin 4.2.2 with koin-compiler-plugin 1.1.0.
 *
 * Everything else this module collects is already a type — `GraphixCustomizer`, `GraphixDirective`,
 * `GraphixInterceptor`, `GraphixExceptionHandler`, `GraphQLScalarType`, `GraphQLEngineCustomizer` —
 * and needs no marker at all. Only a resolver, which is an ordinary class carrying `@QueryMapping`
 * functions, has nothing in common with the next one.
 */
interface GraphixResolver
