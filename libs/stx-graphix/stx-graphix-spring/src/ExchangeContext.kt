package com.softistx.graphix.spring

import com.softistx.graphix.intercept.GraphixChain
import com.softistx.graphix.intercept.get
import graphql.schema.DataFetchingEnvironment
import org.springframework.web.server.ServerWebExchange
import graphql.GraphQLContext as OperationContext

/**
 * The [ServerWebExchange] the operation arrived on, from inside a [com.softistx.graphix.intercept.GraphixInterceptor].
 *
 * The auto-configuration puts it there on every route it registers — POST, SSE and graphql-ws — so
 * this is not an `Optional`: a chain running under it always has one. It throws if the engine was
 * driven by hand through `Graphix.execute(request)` with no context, which is a wiring mistake and
 * not a request to answer.
 */
val GraphixChain.exchange: ServerWebExchange
    get() =
        get<ServerWebExchange>()
            ?: error("no ServerWebExchange in the operation context — this chain did not come from the GraphQL router")

/**
 * The same exchange, from a controller method that already holds a [DataFetchingEnvironment].
 *
 * A method that wants only the exchange should take one as a parameter instead — the
 * auto-configuration registers `contextParameter(ServerWebExchange::class)`, so
 * `fun me(exchange: ServerWebExchange)` builds. This is for the method that needs the environment
 * anyway.
 */
val DataFetchingEnvironment.exchange: ServerWebExchange
    get() = graphQlContext.exchange

/** The exchange out of graphql-java's context bag, which is what a field directive is handed. */
val OperationContext.exchange: ServerWebExchange
    get() =
        get<ServerWebExchange>(ServerWebExchange::class)
            ?: error("no ServerWebExchange in the operation context — the GraphQL router puts one on every request")
