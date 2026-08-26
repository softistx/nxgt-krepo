package com.strange.openapi.emit

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.ApiGroup
import com.strange.openapi.Operation

/** Marks generated files, so nobody edits one by hand and loses the change on the next build. */
public const val GENERATED_KDOC: String = "Generated from the OpenAPI document. Do not edit."

/**
 * One file per API group, holding the interface an emitter built for it.
 *
 * The shape of an interface — its name, its KDoc, one function per operation — is the same for
 * every client style; only the annotations and the function bodies differ, and those are what the
 * caller supplies.
 */
public fun apiFile(
    group: ApiGroup,
    options: EmitOptions,
    annotations: List<AnnotationSpec> = emptyList(),
    operation: (Operation) -> FunSpec,
): FileSpec {
    val type =
        TypeSpec
            .interfaceBuilder(group.name)
            .addKdoc(GENERATED_KDOC)
            .addAnnotations(annotations)
            .apply { group.operations.forEach { addFunction(deprecate(it, operation(it))) } }
            .build()
    return FileSpec.builder(options.packageName, group.name).addType(type).build()
}

/**
 * `@Deprecated` on a generated function.
 *
 * Here rather than in each emitter: it is the document saying an operation is deprecated, not the
 * client style, so neither emitter should have to remember to say it.
 */
private fun deprecate(
    operation: Operation,
    spec: FunSpec,
): FunSpec {
    if (!operation.deprecated) return spec
    val message = operation.deprecatedReason ?: "This operation is deprecated in the OpenAPI document."
    return spec
        .toBuilder()
        .addAnnotation(AnnotationSpec.builder(Deprecated::class).addMember("%S", message).build())
        .build()
}
