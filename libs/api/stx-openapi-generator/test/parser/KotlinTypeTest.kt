package com.softistx.openapi.parser

import com.softistx.openapi.ObjectType
import com.softistx.openapi.TypeRef
import com.softistx.openapi.ValueClassType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(schemas: String) =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
paths:
  /a: { get: { tags: [t], operationId: a, responses: { '200': { description: ok } } } }
components:
  schemas:
${schemas.prependIndent("    ")}
    """.trimIndent()

private val TYPED =
    spec(
        """
Money:
  type: string
  x-kotlin-type: com.example.money.Money
OrderId:
  type: string
  x-kotlin-value-class: true
Attempt:
  type: integer
  x-kotlin-value-class: true
Order:
  type: object
  properties:
    id: { ${'$'}ref: '#/components/schemas/OrderId' }
    total: { ${'$'}ref: '#/components/schemas/Money' }
    paidWith: { type: string, x-kotlin-type: com.example.money.Currency }
    attempts: { type: array, items: { ${'$'}ref: '#/components/schemas/Attempt' } }
        """.trimIndent(),
    )

private val PARSED = OpenApiParser().parse(TYPED)

private fun field(name: String) =
    PARSED.models
        .filterIsInstance<ObjectType>()
        .first { it.name == "Order" }
        .fields
        .first { it.name == name }

/**
 * Two opposite requests, and the reason they share a spec: `x-kotlin-type` says *do not* generate a
 * declaration because the consumer already owns one, and `x-kotlin-value-class` says generate one
 * where the document only described a scalar. Both are about a schema whose Kotlin shape the
 * document knows better than the derivation does.
 */
class KotlinTypeTest :
    FeatureSpec({

        feature("a type the consumer already owns") {
            scenario("a ref to it resolves to that type, not to a generated one") {
                field("total").type shouldBe TypeRef.ExternalRef("com.example.money.Money")
            }

            scenario("nothing is generated for it, and that is not a dangling reference") {
                // The check that every ModelRef names a generated declaration must not fire here:
                // an ExternalRef's whole point is that nothing declares it.
                PARSED.models.none { it.name == "Money" } shouldBe true
            }

            scenario("it works written in place, not only behind a ref") {
                field("paidWith").type shouldBe TypeRef.ExternalRef("com.example.money.Currency")
            }

            scenario("a name that is not qualified fails, quoting it") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(spec("Money: { type: string, x-kotlin-type: Money }"))
                    }

                error.message shouldContain "Money"
                error.message shouldContain "qualified"
            }
        }

        feature("a scalar the document wants as a type of its own") {
            scenario("it becomes a declaration, and refs to it name that declaration") {
                PARSED.models.filterIsInstance<ValueClassType>().map { it.name } shouldContainExactly
                    listOf("Attempt", "OrderId")
                field("id").type shouldBe TypeRef.ModelRef("OrderId")
            }

            scenario("it carries the scalar underneath it, whichever that is") {
                val byName = PARSED.models.filterIsInstance<ValueClassType>().associateBy { it.name }

                byName.getValue("OrderId").base shouldBe TypeRef.StringRef
                byName.getValue("Attempt").base shouldBe TypeRef.IntRef
            }

            scenario("it survives inside a list like any other named type") {
                field("attempts").type shouldBe TypeRef.ListRef(TypeRef.ModelRef("Attempt"))
            }

            scenario("asking for one over an object fails: there is no single value to wrap") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
Wrapped:
  type: object
  x-kotlin-value-class: true
  properties: { a: { type: string } }
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "x-kotlin-value-class"
                error.message shouldContain "scalar"
            }

            scenario("asking for one over an enum fails: the document has said it twice") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec(
                                """
Status:
  type: string
  enum: [a, b]
  x-kotlin-value-class: true
                                """.trimIndent(),
                            ),
                        )
                    }

                error.message shouldContain "enum"
            }
        }
    })
