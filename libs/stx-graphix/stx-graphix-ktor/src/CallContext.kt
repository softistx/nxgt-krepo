package com.softistx.graphix.ktor

import com.softistx.graphix.intercept.GraphixChain
import com.softistx.graphix.intercept.get
import graphql.schema.DataFetchingEnvironment
import io.ktor.server.application.ApplicationCall
import graphql.GraphQLContext as OperationContext

/**
 * The [ApplicationCall] the operation arrived on, from inside an interceptor.
 *
 * The [GraphQL] plugin puts it there on every route — POST, SSE and graphql-ws — so this is not an
 * `Optional`: a chain running under this plugin always has one. It throws if the engine was driven
 * by hand through `Graphix.execute(request)` with no context, which is a wiring mistake and not a
 * request to answer.
 */
val GraphixChain.call: ApplicationCall
    get() = get<ApplicationCall>() ?: error("no ApplicationCall in the operation context — this chain did not come from the GraphQL plugin")

/**
 * The same call, from a resolver that already holds a [DataFetchingEnvironment].
 *
 * A resolver that wants only the call should take one as a parameter instead — the plugin registers
 * `contextParameter(ApplicationCall::class)`, so `fun me(call: ApplicationCall)` builds. This is for
 * the resolver that needs the environment anyway.
 */
val DataFetchingEnvironment.call: ApplicationCall
    get() = graphQlContext.call

/** The call out of graphql-java's context bag, which is what a field directive is handed. */
val OperationContext.call: ApplicationCall
    get() =
        get<ApplicationCall>(ApplicationCall::class)
            ?: error("no ApplicationCall in the operation context — the GraphQL plugin puts one on every request")
