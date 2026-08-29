package com.strange.openapi.parser

import com.strange.openapi.EnumType
import com.strange.openapi.ObjectType
import com.strange.openapi.TypeRef
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private val SPEC =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
paths:
  /orders:
    post:
      tags: [orders]
      operationId: createOrder
      parameters:
        - { name: mode, in: query, schema: { type: string, enum: [fast, slow] } }
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                note: { type: string }
      responses:
        '201':
          description: created
          content:
            application/json:
              schema: { ${'$'}ref: '#/components/schemas/Order' }
  /health:
    get:
      tags: [ops]
      operationId: health
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema:
                type: object
                properties:
                  up: { type: boolean }
components:
  schemas:
    Order:
      type: object
      properties:
        shippingAddress:
          type: object
          properties:
            city: { type: string }
            geo:
              type: object
              properties:
                lat: { type: number }
        lines:
          type: array
          items:
            type: object
            properties:
              sku: { type: string }
        status: { type: string, enum: [draft, final] }
        totals:
          type: object
          additionalProperties:
            type: object
            properties:
              amount: { type: integer }
        meta: { type: object }
    Shipment:
      type: object
      properties:
        status: { type: string, enum: [draft, final] }
    """.trimIndent()

private val PARSED = OpenApiParser().parse(SPEC)

private fun objects() = PARSED.models.filterIsInstance<ObjectType>()

private fun model(name: String) = objects().first { it.name == name }

private fun com.strange.openapi.ApiModel.operation(name: String) = groups.flatMap { it.operations }.first { it.name == name }

private fun fieldType(
    model: String,
    field: String,
) = model(model).fields.first { it.name == field }.type

/**
 * A schema written in place says exactly as much as a named one, but every later stage of the
 * parser is name-driven — so before this pass an inline object came out as raw JSON and an inline
 * enum as a bare `String`, which is most of what "the generator ignored my schema" meant.
 */
class InlineSchemaTest :
    FeatureSpec({

        feature("an inline schema gets a name from where it sits") {
            scenario("a nested object becomes a class the property points at") {
                fieldType("Order", "shippingAddress") shouldBe TypeRef.ModelRef("OrderShippingAddress")
                model("OrderShippingAddress").fields.map { it.name } shouldContainExactly listOf("city", "geo")
            }

            scenario("nesting compounds, so a name says the whole path that reached it") {
                fieldType("OrderShippingAddress", "geo") shouldBe TypeRef.ModelRef("OrderShippingAddressGeo")
                fieldType("OrderShippingAddressGeo", "lat") shouldBe TypeRef.DoubleRef
            }

            scenario("an array element and a map value take a suffix rather than a guessed singular") {
                fieldType("Order", "lines") shouldBe TypeRef.ListRef(TypeRef.ModelRef("OrderLinesItem"))
                fieldType("Order", "totals") shouldBe TypeRef.MapRef(TypeRef.ModelRef("OrderTotalsValue"))
            }

            scenario("an inline enum becomes an enum class, not the scalar underneath it") {
                fieldType("Order", "status") shouldBe TypeRef.ModelRef("OrderStatus")
                PARSED.models
                    .filterIsInstance<EnumType>()
                    .first { it.name == "OrderStatus" }
                    .entries
                    .map { it.wireValue } shouldContainExactly listOf("draft", "final")
            }

            scenario("a schema that becomes no declaration is left exactly as it was") {
                // `{type: object}` with nothing in it is genuinely free-form; naming it would
                // generate an empty class rather than describe anything.
                fieldType("Order", "meta") shouldBe TypeRef.JsonObjectRef
            }
        }

        feature("an operation's own bodies") {
            scenario("an inline request body is a class named after the operation") {
                PARSED
                    .operation("createOrder")
                    .parameters
                    .first { it.name == "body" }
                    .type shouldBe
                    TypeRef.ModelRef("CreateOrderRequest")
                model("CreateOrderRequest").fields.single().name shouldBe "note"
            }

            scenario("an inline response body is too") {
                PARSED.operation("health").returnType shouldBe TypeRef.ModelRef("HealthResponse")
            }

            scenario("a parameter's inline enum is named after the operation and the parameter") {
                PARSED
                    .operation("createOrder")
                    .parameters
                    .first { it.name == "mode" }
                    .type shouldBe
                    TypeRef.ModelRef("CreateOrderMode")
            }
        }

        feature("the same schema written twice") {
            scenario("one class is generated, and both sites point at it") {
                // `Order.status` and `Shipment.status` are the same enum written out twice. Left
                // alone this generates two identical classes a caller cannot pass between; the
                // reused name is the first site in document order, so it is stable.
                PARSED.models.filterIsInstance<EnumType>().map { it.name } shouldContainExactly
                    listOf("CreateOrderMode", "OrderStatus")
                fieldType("Shipment", "status") shouldBe TypeRef.ModelRef("OrderStatus")
            }
        }

        feature("a derived name the document has already used") {
            scenario("the clash fails the parse, naming the name and where it came from") {
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
    Order:
      type: object
      properties:
        line: { type: object, properties: { sku: { type: string } } }
    OrderLine:
      type: object
      properties: { other: { type: string } }
                            """.trimIndent(),
                        )
                    }

                error.message shouldContain "OrderLine"
                error.message shouldContain "property 'line'"
            }
        }
    })
