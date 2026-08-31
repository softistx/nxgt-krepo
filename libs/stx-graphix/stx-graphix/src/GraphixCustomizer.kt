package com.strange.graphix

import graphql.GraphQL

/**
 * Customises [GraphixBuilder] after roots are registered. Spring collects every bean of this
 * type; Ktor `provide`s one (or a [List]).
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
