package com.softistx.openapi.parser

import com.softistx.openapi.TypeRef
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun com.softistx.openapi.ApiModel.operation(name: String) = groups.flatMap { it.operations }.first { it.name == name }

private val INDIRECT =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
paths:
  /orders/{id}:
    parameters:
      - { name: id, in: path, required: true, schema: { type: string } }
    put:
      tags: [orders]
      operationId: replaceOrder
      requestBody: { ${'$'}ref: '#/components/requestBodies/OrderBody' }
      responses:
        '200': { ${'$'}ref: '#/components/responses/OrderRead' }
components:
  requestBodies:
    OrderBody:
      required: true
      content: { application/json: { schema: { ${'$'}ref: '#/components/schemas/Order' } } }
  responses:
    OrderRead:
      description: ok
      content: { application/json: { schema: { ${'$'}ref: '#/components/schemas/Order' } } }
  schemas:
    Order: { type: object, properties: { id: { type: string } } }
    """.trimIndent()

/**
 * A body or a response written once in `components` and referenced from every operation is ordinary
 * in a hand-written document. The content lives behind the reference, so reading it straight off the
 * operation finds nothing — and finding nothing meant a dropped parameter and a `Unit` return type,
 * silently, which is the one thing this generator does not do.
 */
class ComponentBodyRefTest :
    FeatureSpec({

        feature("a body and a response written as components") {
            scenario("a ref'd request body still becomes a body parameter") {
                val operation = OpenApiParser().parse(INDIRECT).operation("replaceOrder")

                operation.parameters.first { it.name == "body" }.let {
                    it.type shouldBe TypeRef.ModelRef("Order")
                    it.required shouldBe true
                }
            }

            scenario("a ref'd response still gives the operation its return type") {
                OpenApiParser().parse(INDIRECT).operation("replaceOrder").returnType shouldBe
                    TypeRef.ModelRef("Order")
            }

            scenario("a reference into components that resolves to nothing fails, naming it") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(INDIRECT.replace("'#/components/responses/OrderRead'", "'#/components/responses/Missing'"))
                    }

                error.message shouldContain "Missing"
            }
        }

        feature("parameters declared once for a whole path") {
            scenario("they reach every operation under it") {
                OpenApiParser()
                    .parse(INDIRECT)
                    .operation("replaceOrder")
                    .parameters
                    .map { it.name } shouldContainExactly
                    listOf("id", "body")
            }
        }
    })
