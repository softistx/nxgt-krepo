package com.softistx.openapi.parser

import com.softistx.openapi.TypeRef
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

        feature("endpoint constants that collide") {
            scenario("a template variable and a literal segment reduce to one constant, and that is fatal") {
                // Nothing downstream would catch this: two properties of one object are not two
                // files, so the writer's duplicate check never sees them and the first symptom
                // would be `conflicting declarations` inside a file nobody wrote.
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
                                |  /orders/{id}:
                                |    get:
                                |      tags: [orders]
                                |      operationId: findOrder
                                |      parameters: [{name: id, in: path, required: true, schema: {type: string}}]
                                |      responses: {'200': {description: ok}}
                                |  /orders/id:
                                |    get: {tags: [orders], operationId: findOrderById, responses: {'200': {description: ok}}}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "GET_ORDERS_ID"
                error.message shouldContain "GET /orders/{id}"
                error.message shouldContain "GET /orders/id"
                error.message shouldContain "x-kotlin-endpoint"
            }

            scenario("the same verb and path under two tags collides too, though neither group does") {
                // The check runs over the whole document because `Endpoints` is one object over the
                // whole document — every other collision check here is per group or per file.
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser(grouping = Grouping.Path).parse(
                            spec(
                                """
                                |  /orders:
                                |    get: {tags: [a], operationId: findOrders, responses: {'200': {description: ok}}}
                                |  /orders/:
                                |    get: {tags: [b], operationId: listOrders, responses: {'200': {description: ok}}}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "GET_ORDERS"
            }

            scenario("x-kotlin-endpoint separates them, which is what the message tells the author") {
                val model =
                    OpenApiParser().parse(
                        spec(
                            """
                            |  /orders/{id}:
                            |    get:
                            |      tags: [orders]
                            |      operationId: findOrder
                            |      parameters: [{name: id, in: path, required: true, schema: {type: string}}]
                            |      responses: {'200': {description: ok}}
                            |  /orders/id:
                            |    get:
                            |      tags: [orders]
                            |      operationId: findOrderById
                            |      x-kotlin-endpoint: GET_ORDERS_ID_LITERAL
                            |      responses: {'200': {description: ok}}
                            """.trimMargin(),
                        ),
                    )

                model.groups
                    .flatMap { it.operations }
                    .map { it.constant }
                    .sorted() shouldContainExactly listOf("GET_ORDERS_ID", "GET_ORDERS_ID_LITERAL")
            }

            scenario("an x-kotlin-endpoint that is not an identifier is refused where it is written") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
                                |  /orders:
                                |    get:
                                |      tags: [orders]
                                |      operationId: findOrders
                                |      x-kotlin-endpoint: not an identifier
                                |      responses: {'200': {description: ok}}
                                """.trimMargin(),
                            ),
                        )
                    }

                error.message shouldContain "x-kotlin-endpoint"
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
