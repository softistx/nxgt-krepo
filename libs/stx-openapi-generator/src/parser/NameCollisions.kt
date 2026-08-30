package com.strange.openapi.parser

import com.strange.openapi.ApiGroup
import com.strange.openapi.ModelType
import com.strange.openapi.Operation

/**
 * Guards against two generated declarations claiming one file.
 *
 * Generated files are named after the declaration they hold, so two declarations with the same
 * name mean two files with the same path — and the second silently overwrites the first, taking
 * its endpoints or its fields with it. Losing an endpoint quietly is the failure mode this
 * generator exists to avoid, so a collision is either resolved here or reported.
 */
internal fun List<ApiGroup>.mergeSameNamedGroups(): List<ApiGroup> =
    groupBy { it.name }
        .map { (name, groups) ->
            // Two tags that differ only in what `interfaceName` strips — `categories-controller`
            // and `categories`, say — describe one interface, so their operations join it.
            val operations = groups.flatMap { it.operations }.sortedBy { it.name }
            ApiGroup(name, operations.requireDistinctNames(name))
        }.sortedBy { it.name }

private fun List<Operation>.requireDistinctNames(group: String): List<Operation> {
    val duplicates = groupBy { it.name }.filterValues { it.size > 1 }
    if (duplicates.isNotEmpty()) {
        throw OpenApiParseException(
            duplicates.entries.joinToString(
                prefix = "interface $group would declare the same function twice: ",
                separator = "; ",
            ) { (name, operations) ->
                "$name from ${operations.joinToString(", ") { "${it.httpMethod} /${it.path}" }}"
            } + ". Give the operations distinct operationIds, or split them across tags.",
        )
    }
    return this
}

/**
 * Two operations whose verb and path reduce to one `Endpoints` entry.
 *
 * A category of its own, and the reason it needs one: the generated `Endpoints` is a single object
 * over the whole document, so this collides across groups where every other check is per group or
 * per file. Nothing downstream would catch it either — two properties of one object are not two
 * files, so the writer's duplicate check never sees them, and the first sign would be *conflicting
 * declarations* in a file nobody wrote.
 *
 * `{id}` and `id` reduce alike because the constant treats a brace as punctuation, which is what
 * makes `GET_ORDERS_ID` readable in the first place.
 */
internal fun List<ApiGroup>.requireDistinctEndpointConstants(): List<ApiGroup> {
    val duplicates =
        flatMap { it.operations }
            .groupBy { it.constant }
            .filterValues { it.size > 1 }
    if (duplicates.isNotEmpty()) {
        throw OpenApiParseException(
            duplicates.entries.joinToString(
                prefix = "operations collide on a generated Endpoints entry: ",
                separator = "; ",
            ) { (constant, operations) ->
                "$constant from ${operations.joinToString(", ") { "${it.httpMethod} /${it.path}" }}"
            } + ". Set x-kotlin-endpoint on one of them to name it something else.",
        )
    }
    return this
}

/** Component schemas whose names differ only in separators or case would share a file. */
internal fun List<ModelType>.requireDistinctNames(): List<ModelType> {
    val duplicates = groupBy { it.name }.filterValues { it.size > 1 }
    if (duplicates.isNotEmpty()) {
        throw OpenApiParseException(
            duplicates.keys.joinToString(
                prefix = "component schemas collide on a generated class name: ",
                separator = "; ",
            ) { "$it (from ${duplicates.getValue(it).size} schemas)" } +
                ". Rename the schemas so their Kotlin names differ.",
        )
    }
    return this
}
