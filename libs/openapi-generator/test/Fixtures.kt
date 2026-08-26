package com.strange.openapi

import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.SourceEmitter

/**
 * One model both emitter tests work from, so a difference between the two clients is visible
 * as a difference in what they emit rather than in what they were asked to emit.
 *
 * The multipart part is deliberately optional: Ktorfit may not emit a nullable `@Part`, while
 * Spring has no such restriction, and that divergence is worth pinning down.
 */
internal val SAMPLE_MODEL: ApiModel =
    ApiModel(
        groups =
            listOf(
                ApiGroup(
                    name = "CategoriesApi",
                    operations =
                        listOf(
                            Operation(
                                name = "findCategory",
                                httpMethod = "GET",
                                path = "categories/{id}",
                                parameters =
                                    listOf(
                                        Param("id", "id", ParamKind.Path, TypeRef.StringRef, required = true),
                                        Param("cursor", "cursor", ParamKind.Query, TypeRef.StringRef, required = false),
                                    ),
                                returnType = TypeRef.ModelRef("Category"),
                                summary = "Get category by ID",
                            ),
                            Operation(
                                name = "createCategory",
                                httpMethod = "POST",
                                path = "categories",
                                parameters =
                                    listOf(
                                        Param("body", "body", ParamKind.Body, TypeRef.ModelRef("CategoryRequest"), required = true),
                                    ),
                                returnType = TypeRef.ListRef(TypeRef.ModelRef("Category")),
                            ),
                            Operation(
                                name = "changePhoto",
                                httpMethod = "PUT",
                                path = "categories/{id}/photo",
                                parameters =
                                    listOf(
                                        Param("file", "file", ParamKind.Part, TypeRef.BinaryRef, required = false),
                                    ),
                                returnType = TypeRef.UnitRef,
                            ),
                        ),
                ),
            ),
        models =
            listOf(
                ModelType(
                    name = "Category",
                    fields =
                        listOf(
                            Field("id", "id", TypeRef.StringRef, required = true),
                            Field("createdAt", "created_at", TypeRef.InstantRef, required = false),
                            Field("meta", "meta", TypeRef.JsonObjectRef, required = false),
                        ),
                ),
            ),
    )

/** Renders an emitter's output as `fully.qualified.Name` to source text. */
internal fun SourceEmitter.render(
    model: ApiModel = SAMPLE_MODEL,
    options: EmitOptions = EmitOptions("com.example.api"),
): Map<String, String> = emit(model, options).associate { "${it.packageName}.${it.name}" to it.toString() }
