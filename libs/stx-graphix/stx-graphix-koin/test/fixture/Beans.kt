package com.softistx.graphix.koin.fixture

import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.put
import com.softistx.graphix.koin.GraphixResolver
import com.softistx.graphix.scalar.graphQLScalar
import com.softistx.graphix.scalar.scalar
import com.softistx.graphix.schema.Argument
import com.softistx.graphix.schema.Directive
import com.softistx.graphix.schema.GraphixDirective
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.contextParameter

class GreetingQueries : GraphixResolver {
    @QueryMapping
    fun hello(): String = "world"
}

class EchoQueries : GraphixResolver {
    @QueryMapping
    fun echo(
        @Argument text: String,
    ): String = text
}

/** Not a [GraphixResolver], so `fromKoin` must leave it out even though it is a Koin single. */
class UnmarkedQueries {
    @QueryMapping
    fun secret(): String = "should not be in the schema"
}

/** Uses the directive, so the spec can prove it was wired rather than merely named. */
class ShoutQueries : GraphixResolver {
    @QueryMapping
    @Directive("uppercase")
    fun shout(): String = "quiet"
}

data class Caller(
    val name: String,
)

class CallerQueries : GraphixResolver {
    @QueryMapping
    fun who(caller: Caller): String = caller.name
}

/**
 * How an application registers its *own* context type: whoever fills the context registers it, and
 * here that is the interceptor below. A `GraphixCustomizer` single is collected by `fromKoin()`.
 */
fun callerCustomizer() = GraphixCustomizer { contextParameter(Caller::class) }

fun callerInterceptor(name: String) =
    GraphixInterceptor {
        put(Caller(name))
        proceed()
    }

fun moneyCustomizer() =
    GraphixCustomizer {
        scalar(graphQLScalar("Money") { serialize { value -> value.toString() } })
    }

fun uppercaseDirective() =
    GraphixDirective("uppercase") {
        val value = proceed()
        (value as? String)?.uppercase() ?: value
    }
