package com.softistx.graphix

import graphql.GraphQL

/**
 * Customises [GraphixBuilder] after roots are registered. Spring collects every bean of this type
 * through an `ObjectProvider`; `fromKoin()` every single bound to it. Under Ktor without Koin it is
 * registered by hand, in `customize { }`.
 */
fun interface GraphixCustomizer {
    fun GraphixBuilder.customize()
}

/**
 * Customises graphql-java's [GraphQL.Builder] after the schema is built. Instrumentation,
 * execution strategy, a value unboxer.
 */
fun interface GraphQLEngineCustomizer {
    fun GraphQL.Builder.customize()
}

fun GraphixBuilder.customize(customizer: GraphixCustomizer) {
    with(customizer) { customize() }
}

fun GraphixBuilder.engine(customizer: GraphQLEngineCustomizer) {
    addEngineCustomizer(customizer)
}

fun GraphixBuilder.engine(block: GraphQL.Builder.() -> Unit) {
    engine(GraphQLEngineCustomizer(block))
}
