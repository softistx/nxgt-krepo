package com.strange.openapi.parser

import com.strange.openapi.ApiModel
import com.strange.openapi.EnumType
import com.strange.openapi.ObjectType
import com.strange.openapi.TypeRef
import com.strange.openapi.UnionType
import com.strange.openapi.ValueClassType

/**
 * Every [TypeRef.ModelRef] must name a declaration this run actually generates.
 *
 * The invariant is older than this check: a `ModelRef` naming a class nobody emits produces
 * generated source that will not compile, and the error lands in a file the author never wrote.
 * Two things can now break it from the document's side — `x-kotlin-skip` leaving out a schema
 * something still points at, and a `$ref` to a component that does not exist, which until now
 * became a `ModelRef` to a name nothing would ever declare.
 *
 * So it is checked once, over the finished model, rather than trusted at each of the sites that
 * builds a reference.
 */
internal fun ApiModel.requireEveryRefGenerated() {
    val declared = models.mapTo(mutableSetOf()) { it.name }
    val missing = linkedMapOf<String, MutableList<String>>()

    fun record(
        name: String,
        where: String,
    ) {
        if (name !in declared) missing.getOrPut(name) { mutableListOf() } += where
    }

    fun check(
        type: TypeRef,
        where: String,
    ) {
        when (type) {
            is TypeRef.ModelRef -> record(type.name, where)
            is TypeRef.ListRef -> check(type.element, where)
            is TypeRef.MapRef -> check(type.value, where)
            else -> Unit
        }
    }

    groups.forEach { group ->
        group.operations.forEach { operation ->
            check(operation.returnType, "${group.name}.${operation.name} returns it")
            operation.errors.forEach { error ->
                error.type?.let { check(it, "${group.name}.${operation.name} fails with it on ${error.status}") }
            }
            operation.parameters.forEach { check(it.type, "${group.name}.${operation.name} takes it as '${it.name}'") }
        }
    }
    models.forEach { model ->
        when (model) {
            is ObjectType -> {
                model.fields.forEach { check(it.type, "${model.name}.${it.name} is typed with it") }
                model.implements.forEach { record(it, "${model.name} implements it") }
            }

            is UnionType -> {
                model.subtypes.forEach { record(it.name, "${model.name} has it as a member") }
            }

            // Neither declares a reference of its own: an enum's values and a value class's
            // scalar are both types this generator already knows how to write.
            is EnumType, is ValueClassType -> {
                Unit
            }
        }
    }

    if (missing.isEmpty()) return
    throw OpenApiParseException(
        missing.entries.joinToString(
            prefix = "the document refers to schemas that are not generated: ",
            separator = "; ",
        ) { (name, wheres) -> "'$name' (${wheres.joinToString(", ")})" } +
            ". Either the ${'$'}ref names a schema the document does not define, or the schema is left " +
            "out with ${Ext.SKIP} or ${Ext.INTERNAL} while something still points at it.",
    )
}
