package com.strange.graphix.spring

import com.strange.graphix.GraphixBuilder
import com.strange.graphix.schema.Batch
import com.strange.graphix.schema.BatchLoading
import com.strange.graphix.schema.Field
import com.strange.graphix.schema.Mutation
import com.strange.graphix.schema.Query
import com.strange.graphix.schema.Subscription
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

/** Registers [instance] as query, mutation, subscription, type fields, loaders, or any mix. */
internal fun GraphixBuilder.addController(instance: Any) {
    val functions = instance::class.memberFunctions
    if (functions.any { it.hasAnnotation<Query>() }) query(instance)
    if (functions.any { it.hasAnnotation<Mutation>() }) mutation(instance)
    if (functions.any { it.hasAnnotation<Subscription>() }) subscription(instance)
    if (functions.any { it.hasAnnotation<Field>() || it.hasAnnotation<Batch>() }) type(instance)
    if (functions.any { it.hasAnnotation<BatchLoading>() }) loader(instance)
}
