package com.softistx.graphix.spring

import com.softistx.graphix.GraphixBuilder
import com.softistx.graphix.schema.BatchMapping
import com.softistx.graphix.schema.MutationMapping
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.SchemaMapping
import com.softistx.graphix.schema.SubscriptionMapping
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
