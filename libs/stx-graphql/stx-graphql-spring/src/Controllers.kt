package com.strange.graphql.spring

import com.strange.graphql.GraphQlBuilder
import com.strange.graphql.schema.Mutation
import com.strange.graphql.schema.Query
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

internal fun GraphQlBuilder.addController(instance: Any) {
    val functions = instance::class.memberFunctions
    if (functions.any { it.hasAnnotation<Query>() }) query(instance)
    if (functions.any { it.hasAnnotation<Mutation>() }) mutation(instance)
}
