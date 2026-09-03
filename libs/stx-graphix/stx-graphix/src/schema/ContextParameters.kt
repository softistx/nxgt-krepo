package com.softistx.graphix.schema

import com.softistx.graphix.GraphixBuilder
import kotlin.reflect.KClass

/**
 * Lets a resolver take [type] as a plain parameter, resolved from the operation context by
 * `KClass` rather than read off the document.
 *
 * ```kotlin
 * // stx-graphix-ktor, once, when it builds the engine
 * contextParameter(ApplicationCall::class)
 *
 * // and then, in any resolver
 * @QueryMapping fun me(call: ApplicationCall): String = call.request.headers["X-User"] ?: "anonymous"
 * ```
 *
 * `DataFetchingEnvironment` and graphql-java's `GraphQLContext` need no registration — they are
 * graphql-java's own and this module knows them. This is for the types a *framework* supplies:
 * `ApplicationCall` in Ktor, `ServerWebExchange` in Spring. Whoever registers the type is also
 * whoever puts the value in `execute`'s context map; registering it here only says that such a
 * parameter is not a GraphQL argument.
 *
 * **Registered rather than inferred.** A GraphQL argument is always `@Argument`, so leaving these
 * unannotated costs no ambiguity — but a type nobody registered still fails schema build naming the
 * parameter, which is the error a forgotten `@Argument` has always produced and keeps producing.
 * The core names no framework type of its own, so a third stack is one call and no edit here.
 */
fun GraphixBuilder.contextParameter(type: KClass<*>) {
    addContextParameter(type)
}

/** [contextParameter], with the type as a parameter. */
inline fun <reified T : Any> GraphixBuilder.contextParameter() = contextParameter(T::class)
