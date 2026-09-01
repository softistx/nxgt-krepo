package com.softistx.openapi.parser

import com.softistx.openapi.ObjectType
import com.softistx.openapi.TypeRef
import com.softistx.openapi.UnionType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private val RENAMED =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
tags:
  - name: orders-controller
    x-kotlin-name: Orders
paths:
  /orders:
    post:
      tags: [orders-controller]
      operationId: createOrderV2
      x-kotlin-name: place
      parameters:
        - name: dry-run
          in: query
          x-kotlin-name: preview
          schema: { type: boolean }
      requestBody:
        required: true
        content:
          application/json:
            schema: { ${'$'}ref: '#/components/schemas/order_request' }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { ${'$'}ref: '#/components/schemas/payment' }
components:
  schemas:
    order_request:
      type: object
      x-kotlin-name: PlaceOrder
      properties:
        ship_to:
          type: object
          x-kotlin-name: Destination
          properties:
            city: { type: string }
        note:
          type: string
          x-kotlin-name: memo
    payment:
      x-kotlin-name: Payment
      oneOf:
        - { ${'$'}ref: '#/components/schemas/card_payment' }
        - { ${'$'}ref: '#/components/schemas/cash_payment' }
      discriminator:
        propertyName: kind
    card_payment:
      type: object
      x-kotlin-name: CardPayment
      properties:
        kind: { type: string }
        pan: { type: string }
    cash_payment:
      type: object
      properties:
        kind: { type: string }
        received: { type: number }
    """.trimIndent()

private val PARSED = OpenApiParser().parse(RENAMED)

private fun objects() = PARSED.models.filterIsInstance<ObjectType>()

private fun model(name: String) = objects().first { it.name == name }

private fun operation() =
    PARSED.groups
        .single()
        .operations
        .single()

/**
 * `x-kotlin-name` is the first rule a *document* can change about a derived name, and the derivation
 * used to happen independently at six call sites. A rename that reached the declaration but not one
 * of its references would emit a `ModelRef` naming a class nobody generates — which is why the
 * references are what this spec asserts, not the declarations.
 */
class KotlinNameTest :
    FeatureSpec({

        feature("a name the document states, everywhere the name is used") {
            scenario("a renamed schema is declared once, under that name") {
                PARSED.models.map { it.name } shouldContainAll
                    listOf("PlaceOrder", "Destination", "Payment", "CardPayment", "CashPayment")
            }

            scenario("a property's type follows the rename") {
                model("PlaceOrder").fields.first { it.name == "shipTo" }.type shouldBe
                    TypeRef.ModelRef("Destination")
            }

            scenario("a union's members and its generated fallback follow it too") {
                val payment = PARSED.models.filterIsInstance<UnionType>().single()

                payment.name shouldBe "Payment"
                payment.subtypes.map { it.name } shouldBe listOf("CardPayment", "CashPayment")
                payment.fallback shouldBe "UnknownPayment"
                model("CardPayment").implements shouldBe listOf("Payment")
            }

            scenario("a request body and a return type follow it") {
                operation().parameters.first { it.kind.name == "Body" }.type shouldBe
                    TypeRef.ModelRef("PlaceOrder")
                operation().returnType shouldBe TypeRef.ModelRef("Payment")
            }

            scenario("an inline schema takes the stated name over the one derived from its path") {
                // Left alone this is `OrderRequestShipTo`, which is the escape hatch's whole point.
                objects().map { it.name } shouldContainAll listOf("Destination")
                objects().none { it.name == "OrderRequestShipTo" } shouldBe true
            }
        }

        feature("names that are not a schema's") {
            scenario("a property keeps its wire name while its Kotlin name changes") {
                model("PlaceOrder").fields.first { it.name == "memo" }.wireName shouldBe "note"
            }

            scenario("an operation and a parameter are renamed the same way") {
                operation().name shouldBe "place"
                operation().parameters.first { it.name == "preview" }.wireName shouldBe "dry-run"
            }

            scenario("a tag names its interface outright, prefix and suffix included") {
                // `orders-controller` would derive `OrdersApi`; a document that states the name is
                // not asking for one to be derived.
                PARSED.groups.single().name shouldBe "Orders"
            }
        }

        feature("a name that cannot work") {
            scenario("one that is not an identifier fails, quoting it") {
                val error = shouldThrow<OpenApiParseException> { parse("x-kotlin-name: order id") }

                error.message shouldContain "order id"
                error.message shouldContain "identifier"
            }

            scenario("one that is not a string fails, saying what the document gave") {
                val error = shouldThrow<OpenApiParseException> { parse("x-kotlin-name: 42") }

                error.message shouldContain "must be a string"
                error.message shouldContain "a number"
            }

            scenario("renaming one schema onto another is a collision, not a silent overwrite") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
paths:
  /a: { get: { tags: [t], operationId: a, responses: { '200': { description: ok } } } }
components:
  schemas:
    Order: { type: object, properties: { id: { type: string } } }
    order_v2:
      type: object
      x-kotlin-name: Order
      properties: { id: { type: string } }
                            """.trimIndent(),
                        )
                    }

                error.message shouldContain "Order"
            }
        }
    })

private fun parse(extension: String) =
    OpenApiParser().parse(
        """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
paths:
  /a: { get: { tags: [t], operationId: a, responses: { '200': { description: ok } } } }
components:
  schemas:
    Order:
      type: object
      $extension
      properties:
        id: { type: string }
        """.trimIndent(),
    )
