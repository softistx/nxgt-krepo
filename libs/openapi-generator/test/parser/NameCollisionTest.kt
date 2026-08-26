package com.strange.openapi.parser

import com.strange.openapi.TypeRef
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(
    paths: String,
    schemas: String = "",
) = """
    |openapi: 3.0.1
    |info: {title: t, version: "1"}
    |paths:
    |$paths
    |components:
    |  schemas:
    |    Ok: {type: object, properties: {ok: {type: boolean}}}
    |$schemas
    """.trimMargin()

class NameCollisionTest :
    FeatureSpec({

        feature("names that merge") {
            scenario("tags that resolve to one interface name are merged, not dropped") {
                val model =
                    OpenApiParser().parse(
                        spec(
                            """
                            |  /categories:
                            |    get: {tags: [categories-controller], operationId: findCategories, responses: {'200': {description: ok}}}
                            |  /categories/{id}:
                            |    get:
                            |      tags: [categories]
                            |      operationId: findCategory
                            |      parameters: [{name: id, in: path, required: true, schema: {type: string}}]
                            |      responses: {'200': {description: ok}}
                            """.trimMargin(),
                        ),
                    )

                model.groups.map { it.name } shouldContainExactly listOf("CategoriesApi")
                model.groups
                    .single()
                    .operations
                    .map { it.name } shouldContainExactly listOf("findCategories", "findCategory")
            }
        }

        feature("names that collide") {
            scenario("two operations that would declare the same function fail, naming both") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
                                |  /a:
                                |    get: {tags: [t], operationId: find-thing, responses: {'200': {description: ok}}}
                                |  /b:
                                |    get: {tags: [t], operationId: findThing, responses: {'200': {description: ok}}}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "findThing"
                error.message shouldContain "GET /a"
                error.message shouldContain "GET /b"
            }

            scenario("schemas that collide on a class name fail rather than overwrite each other") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                "|  /a: {get: {tags: [t], operationId: a, responses: {'200': {description: ok}}}}".trimMargin(),
                                """
                                |    page_info: {type: object, properties: {size: {type: integer}}}
                                |    pageInfo: {type: object, properties: {total: {type: integer}}}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "PageInfo"
            }
        }

        feature("ambiguous responses") {
            scenario("the lowest 2xx decides the return type, whatever order the document lists") {
                val model =
                    OpenApiParser().parse(
                        spec(
                            """
                            |  /a:
                            |    post:
                            |      tags: [t]
                            |      operationId: create
                            |      responses:
                            |        '201': {description: created, content: {application/json: {schema: {type: integer}}}}
                            |        '200': {description: ok, content: {application/json: {schema: {type: string}}}}
                            """.trimMargin(),
                        ),
                    )

                model.groups
                    .single()
                    .operations
                    .single()
                    .returnType shouldBe TypeRef.StringRef
            }
        }
    })
