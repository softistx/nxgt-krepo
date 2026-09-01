package com.softistx.openapi.parser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(body: String) =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
$body
    """.trimIndent()

private val WITH_EXCLUSIONS =
    spec(
        """
paths:
  /orders:
    get:
      tags: [orders]
      operationId: listOrders
      responses: { '200': { description: ok } }
    post:
      tags: [orders]
      operationId: seedOrders
      x-internal: true
      responses: { '200': { description: ok } }
  /debug:
    get:
      tags: [debug]
      operationId: dumpState
      x-kotlin-skip: true
      responses: { '200': { description: ok } }
components:
  schemas:
    Order:
      type: object
      properties: { id: { type: string } }
    DebugState:
      type: object
      x-internal: true
      properties: { heap: { type: integer } }
        """.trimIndent(),
    )

/**
 * A document is entitled to describe more than its clients should see. `x-internal` is the spelling
 * Redocly, Bump and ReadMe already use to keep an endpoint out of a published reference, so a
 * document that hides one there should not have it turn up in a generated client.
 *
 * The interesting half is what happens when something *else* still points at what was left out.
 */
class ExclusionTest :
    FeatureSpec({

        val parsed = OpenApiParser().parse(WITH_EXCLUSIONS)

        feature("what the document asks to be left out") {
            scenario("an excluded operation is gone, in either spelling") {
                parsed.groups
                    .single()
                    .operations
                    .map { it.name } shouldContainExactly listOf("listOrders")
            }

            scenario("a group left with no operations produces no interface at all") {
                // `/debug` had exactly one operation, and an empty interface is not a smaller
                // client, it is a file that says nothing.
                parsed.groups.map { it.name } shouldContainExactly listOf("OrdersApi")
            }

            scenario("an excluded schema is not generated") {
                parsed.models.map { it.name } shouldContainExactly listOf("Order")
            }
        }

        feature("something still pointing at what was left out") {
            scenario("the parse fails, naming the schema and what refers to it") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
paths:
  /a: { get: { tags: [t], operationId: a, responses: { '200': { description: ok } } } }
components:
  schemas:
    Order:
      type: object
      properties:
        state: { ${'$'}ref: '#/components/schemas/DebugState' }
    DebugState:
      type: object
      x-kotlin-skip: true
      properties: { heap: { type: integer } }
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "DebugState"
                error.message shouldContain "Order.state"
                error.message shouldContain "x-kotlin-skip"
            }

            scenario("a ref to a schema the document never defines fails the same way") {
                // Before this check that became a `ModelRef` to a name nothing declares, and the
                // error surfaced as a compile failure inside a file nobody wrote.
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
paths:
  /a:
    get:
      tags: [t]
      operationId: a
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { ${'$'}ref: '#/components/schemas/Odrer' }
components:
  schemas:
    Order:
      type: object
      properties: { id: { type: string } }
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "Odrer"
            }
        }

        feature("a value that is not a flag") {
            scenario("it fails saying what the document gave") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
paths:
  /a: { get: { tags: [t], operationId: a, responses: { '200': { description: ok } } } }
components:
  schemas:
    Order:
      type: object
      x-kotlin-skip: "yes"
      properties: { id: { type: string } }
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "must be true or false"
                error.message shouldContain "a string"
            }
        }

        feature("an operation that is kept") {
            scenario("it is untouched by any of this") {
                parsed.groups
                    .single()
                    .operations
                    .single()
                    .httpMethod shouldBe "GET"
            }
        }
    })
