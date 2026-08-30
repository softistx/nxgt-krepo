package com.strange.graphix.spring

import com.strange.graphix.GraphixBuilder
import com.strange.graphix.schema.BatchMapping
import com.strange.graphix.schema.MutationMapping
import com.strange.graphix.schema.QueryMapping
import com.strange.graphix.schema.SchemaMapping
import com.strange.graphix.schema.SubscriptionMapping
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberFunctions

/** Registers [instance] as query, mutation, subscription, type fields, or any mix of those. */
internal fun GraphixBuilder.addController(instance: Any) {
    val functions = instance::class.memberFunctions
    if (functions.any { it.hasAnnotation<QueryMapping>() }) query(instance)
    if (functions.any { it.hasAnnotation<MutationMapping>() }) mutation(instance)
    if (functions.any { it.hasAnnotation<SubscriptionMapping>() }) subscription(instance)
    if (functions.any { it.hasAnnotation<SchemaMapping>() || it.hasAnnotation<BatchMapping>() }) type(instance)
}
