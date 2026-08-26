package com.strange.openapi.parser

import com.strange.openapi.ParamKind
import com.strange.openapi.TypeRef
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private val SPEC =
    """
openapi: 3.1.0
info: { title: Test, version: 1.0.0 }
paths:
  /categories:
    get:
      tags: [categories-controller]
      operationId: findCategories
      parameters:
        - { name: cursor, in: query, schema: { type: string } }
        - { name: first, in: query, required: true, schema: { type: integer, format: int32 } }
      responses:
        '200':
          content:
            application/json:
              schema: { type: array, items: { ${'$'}ref: '#/components/schemas/Category' } }
  /categories/{id}:
    delete:
      tags: [categories-controller]
      operationId: deleteCategory
      parameters:
        - { name: id, in: path, required: true, schema: { type: string } }
      responses:
        '204': { description: gone }
  /tags:
    post:
      tags: [tags-controller]
      operationId: createTag
      requestBody:
        required: true
        content:
          application/json:
            schema: { ${'$'}ref: '#/components/schemas/TagRequest' }
      responses:
        '200':
          content:
            application/json:
              schema: { ${'$'}ref: '#/components/schemas/Tag' }
components:
  schemas:
    Category:
      type: object
      required: [id, name]
      properties:
        id: { type: string }
        name: { type: string }
        created_at: { type: string, format: date-time }
        weight: { type: integer, format: int64 }
    Tag:
      type: object
      required: [id]
      properties:
        id: { type: string }
    TagRequest:
      type: object
      properties:
        name: { type: string }
    """.trimIndent()

class OpenApiParserTest :
    FeatureSpec({

        val model = OpenApiParser().parse(SPEC)

        feature("grouping") {
            scenario("groups operations by tag, one interface per tag") {
                model.groups.map { it.name } shouldContainExactly listOf("CategoriesApi", "TagsApi")
                model.groups
                    .first { it.name == "CategoriesApi" }
                    .operations
                    .map { it.name } shouldContainExactly
                    listOf("deleteCategory", "findCategories")
            }

            scenario("grouping strategies change the interface split") {
                OpenApiParser(Grouping.None).parse(SPEC).groups.map { it.name } shouldContainExactly listOf("DefaultApi")
                OpenApiParser(Grouping.Path).parse(SPEC).groups.map { it.name } shouldContainExactly
                    listOf("CategoriesApi", "TagsApi")
            }
        }

        feature("operations") {
            scenario("maps method, path and query parameters") {
                val op =
                    model.groups
                        .first { it.name == "CategoriesApi" }
                        .operations
                        .first { it.name == "findCategories" }
                op.httpMethod shouldBe "GET"
                op.path shouldBe "categories"
                op.parameters.map { it.name to it.required } shouldContainExactly listOf("cursor" to false, "first" to true)
                op.parameters.all { it.kind == ParamKind.Query } shouldBe true
                op.returnType shouldBe TypeRef.ListRef(TypeRef.ModelRef("Category"))
            }

            scenario("path parameters are always required") {
                val op =
                    model.groups
                        .first { it.name == "CategoriesApi" }
                        .operations
                        .first { it.name == "deleteCategory" }
                op.parameters.single().kind shouldBe ParamKind.Path
                op.parameters.single().required shouldBe true
                op.returnType shouldBe TypeRef.UnitRef
            }

            scenario("json request bodies become a single body parameter") {
                val op =
                    model.groups
                        .first { it.name == "TagsApi" }
                        .operations
                        .single()
                val body = op.parameters.single()
                body.kind shouldBe ParamKind.Body
                body.type shouldBe TypeRef.ModelRef("TagRequest")
                op.returnType shouldBe TypeRef.ModelRef("Tag")
            }
        }

        feature("models") {
            scenario("models keep their component names and map formats") {
                val category = model.models.first { it.name == "Category" }
                category.fields.map { it.name } shouldContainExactly listOf("id", "name", "createdAt", "weight")
                category.fields.first { it.name == "createdAt" }.let {
                    it.type shouldBe TypeRef.InstantRef
                    it.wireName shouldBe "created_at"
                    it.required shouldBe false
                }
                category.fields.first { it.name == "weight" }.type shouldBe TypeRef.LongRef
                category.fields.first { it.name == "id" }.required shouldBe true
            }
        }

        feature("invalid specs") {
            scenario("an operation without an operationId fails loudly") {
                val broken =
                    """
                    openapi: 3.1.0
                    info: { title: T, version: 1.0.0 }
                    paths:
                      /x:
                        get:
                          responses: { '200': { description: ok } }
                    """.trimIndent()
                shouldThrow<OpenApiParseException> { OpenApiParser().parse(broken) }
                    .message!! shouldContain "operationId"
            }

            scenario("unsupported request media types fail loudly") {
                val broken =
                    """
                    openapi: 3.1.0
                    info: { title: T, version: 1.0.0 }
                    paths:
                      /x:
                        post:
                          operationId: doX
                          requestBody:
                            content:
                              application/xml: { schema: { type: string } }
                          responses: { '200': { description: ok } }
                    """.trimIndent()
                shouldThrow<OpenApiParseException> { OpenApiParser().parse(broken) }
                    .message!! shouldContain "application/xml"
            }
        }
    })
