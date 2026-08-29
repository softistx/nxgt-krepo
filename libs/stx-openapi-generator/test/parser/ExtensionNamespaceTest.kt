package com.strange.openapi.parser

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.string.shouldContain

private fun spec(body: String) =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
$body
    """.trimIndent()

/**
 * `x-kotlin-*` is this generator's namespace, so a key in it that means nothing here is a typo — and
 * a setting that quietly never applies is the failure mode this generator refuses everywhere else.
 * Everything outside the namespace belongs to some other toolchain and is none of our business.
 */
class ExtensionNamespaceTest :
    FeatureSpec({

        feature("a key inside our namespace") {
            scenario("one this generator does not implement fails, naming the nearest one that exists") {
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
      x-kotlin-nmae: PlacedOrder
      properties: { id: { type: string } }
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "x-kotlin-nmae"
                error.message shouldContain "x-kotlin-name"
                error.message shouldContain "schema Order"
            }

            scenario("one buried in a nested property is caught too") {
                // The whole document is walked, not only the parts a feature happens to read.
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
        lines:
          type: array
          items:
            type: object
            properties:
              sku: { type: string, x-kotlin-rename: code }
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "x-kotlin-rename"
                error.message shouldContain "property 'sku'"
            }

            scenario("one on an operation names the operation") {
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
      x-kotlin-names: place
      responses: { '200': { description: ok } }
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "x-kotlin-names"
                error.message shouldContain "/a"
            }
        }

        feature("a key that belongs to someone else") {
            scenario("it is ignored, however much of it there is") {
                shouldNotThrowAny {
                    OpenApiParser().parse(
                        spec(
                            """
x-amazon-apigateway-request-validators: { all: { validateRequestBody: true } }
paths:
  /a:
    x-summary: A path
    get:
      tags: [t]
      operationId: a
      x-codegen-request-body-name: payload
      x-amazon-apigateway-integration: { type: aws_proxy }
      responses: { '200': { description: ok, x-readme-code-samples: [] } }
components:
  schemas:
    Order:
      type: object
      x-stoplight: { id: abc }
      properties: { id: { type: string, x-faker: random.uuid } }
                            """.trimIndent(),
                        ),
                    )
                }
            }
        }
    })
