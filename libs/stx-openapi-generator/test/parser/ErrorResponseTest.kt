package com.strange.openapi.parser

import com.strange.openapi.TypeRef
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(body: String) =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
$body
    """.trimIndent()

private val WITH_ERRORS =
    spec(
        """
paths:
  /orders/{id}:
    get:
      tags: [orders]
      operationId: findOrder
      parameters:
        - { name: id, in: path, required: true, schema: { type: string } }
      responses:
        '200': { description: ok, content: { application/json: { schema: { ${'$'}ref: '#/components/schemas/Order' } } } }
        '404': { ${'$'}ref: '#/components/responses/NotFound' }
        '401': { description: no token }
        '400':
          description: bad
          content: { application/json: { schema: { ${'$'}ref: '#/components/schemas/Validation' } } }
        default: { ${'$'}ref: '#/components/responses/NotFound' }
components:
  responses:
    NotFound:
      description: gone
      content: { application/json: { schema: { ${'$'}ref: '#/components/schemas/Problem' } } }
  schemas:
    Order:
      type: object
      properties: { id: { type: string } }
    Problem:
      type: object
      properties: { detail: { type: string } }
    Validation:
      type: object
      properties: { field: { type: string } }
        """.trimIndent(),
    )

/**
 * A document says as much about how an operation fails as about how it succeeds — 169 of the
 * responses in `examples/demo-api/openapi.yaml` are non-2xx — and until this the parser read none of
 * it. The return type is one thing a caller cannot get wrong; the failure is the half a caller has
 * to guess at.
 */
class ErrorResponseTest :
    FeatureSpec({

        val operation =
            OpenApiParser()
                .parse(WITH_ERRORS)
                .groups
                .single()
                .operations
                .single()

        feature("which responses become errors") {

            scenario("the 2xx is the return type and never an error") {
                operation.returnType shouldBe TypeRef.ModelRef("Order")
                operation.errors.map { it.status } shouldContainExactly listOf("400", "401", "404", "default")
            }

            scenario("they are ordered by status, with default last") {
                operation.errors.last().status shouldBe "default"
                operation.errors.last().code shouldBe null
            }
        }

        feature("what each error carries") {

            scenario("a body behind a components/responses ref is resolved, not dropped") {
                operation.errors.single { it.status == "404" }.type shouldBe TypeRef.ModelRef("Problem")
            }

            scenario("an inline body is typed like any other schema") {
                operation.errors.single { it.status == "400" }.type shouldBe TypeRef.ModelRef("Validation")
            }

            scenario("a status with no body is kept, with no type") {
                val unauthorized = operation.errors.single { it.status == "401" }
                unauthorized.type shouldBe null
                unauthorized.description shouldBe "no token"
            }

            scenario("the description comes from the resolved response, not the reference site") {
                operation.errors.single { it.status == "404" }.description shouldBe "gone"
            }
        }

        feature("an error model is a reference like any other") {

            scenario("skipping a schema something still fails with is a parse failure") {
                val message =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            WITH_ERRORS.replace(
                                "    Problem:\n      type: object",
                                "    Problem:\n      x-kotlin-skip: true\n      type: object",
                            ),
                        )
                    }.message
                message.shouldNotBeNull() shouldContain "Problem"
                message shouldContain "fails with it on 404"
            }
        }
    })
