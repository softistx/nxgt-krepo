package com.strange.openapi.parser

import com.strange.openapi.ObjectType
import com.strange.openapi.TypeRef
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** OpenAPI 3.1, where a nullable string is spelled as a type set. */
private val SPEC_31 =
    """
openapi: 3.1.0
info: { title: T, version: 1.0.0 }
paths:
  /a:
    get: { tags: [t], operationId: a, responses: { '200': { description: ok } } }
components:
  schemas:
    Shapes:
      type: object
      required: [nullFirst, nullLast]
      properties:
        nullFirst: { type: ["null", "string"] }
        nullLast: { type: ["string", "null"] }
        counts: { type: object, additionalProperties: { type: integer, format: int64 } }
        anything: { type: object }
    """.trimIndent()

/** OpenAPI 3.0, where the same idea is spelled `nullable: true`, and where defaults live. */
private val SPEC_30 =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
paths:
  /a:
    get: { tags: [t], operationId: a, responses: { '200': { description: ok } } }
components:
  schemas:
    Defaults:
      type: object
      description: A schema that exercises every use-site fact.
      required: [id, note]
      properties:
        id: { type: string, description: '  The unique identifier.  ' }
        note: { type: string, nullable: true }
        legacy: { type: string, deprecated: true }
        migrated: { type: string, x-nullable: true }
        ref: { type: string, format: uuid }
        day: { type: string, format: date }
        size: { type: integer, default: 20 }
        ratio: { type: number, default: 0.5 }
        label: { type: string, default: '' }
        active: { type: boolean, default: true }
        debug: { type: string, default: null }
        tags: { type: array, items: { type: string }, default: [] }
    """.trimIndent()

private fun models(spec: String) = OpenApiParser().parse(spec).models.filterIsInstance<ObjectType>()

private fun field(
    spec: String,
    model: String,
    name: String,
) = models(spec).first { it.name == model }.fields.first { it.name == name }

/**
 * The facts a schema states about a *use* of a type — may it be absent, may it be null, what does
 * it fall back to — as opposed to which type it is. Each of these was silently wrong or fatal
 * before, and each is reachable from an ordinary document.
 */
class SchemaFidelityTest :
    FeatureSpec({

        feature("nullability is independent of requiredness") {
            scenario("a 3.1 type set is nullable whichever order it lists null in") {
                // Written the other way round this used to fail the build outright:
                // `unsupported schema type 'null'`.
                field(SPEC_31, "Shapes", "nullFirst").let {
                    it.type shouldBe TypeRef.StringRef
                    it.nullable shouldBe true
                    it.required shouldBe true
                }
                field(SPEC_31, "Shapes", "nullLast").let {
                    it.type shouldBe TypeRef.StringRef
                    it.nullable shouldBe true
                }
            }

            scenario("a document converted from Swagger 2 still says it with x-nullable") {
                field(SPEC_30, "Defaults", "migrated").nullable shouldBe true
            }

            scenario("a required property can still be nullable") {
                // The property must be sent, and may be sent as null. Deriving nullability from
                // `required` alone made this a non-null Kotlin type that threw on the first null.
                field(SPEC_30, "Defaults", "note").let {
                    it.required shouldBe true
                    it.nullable shouldBe true
                }
                field(SPEC_30, "Defaults", "id").nullable shouldBe false
            }
        }

        feature("defaults") {
            scenario("the document's default is carried, whatever its type") {
                field(SPEC_30, "Defaults", "size").default shouldBe "20"
                field(SPEC_30, "Defaults", "ratio").default shouldBe "0.5"
                field(SPEC_30, "Defaults", "active").default shouldBe "true"
            }

            scenario("an empty default is a value, not an absent one") {
                field(SPEC_30, "Defaults", "label").default shouldBe ""
            }

            scenario("an explicit null default says nothing optionality does not already say") {
                field(SPEC_30, "Defaults", "debug").default.shouldBeNull()
            }
        }

        feature("the document's own words") {
            scenario("a description survives onto the declaration and the property") {
                models(SPEC_30).first { it.name == "Defaults" }.doc shouldBe
                    "A schema that exercises every use-site fact."
                field(SPEC_30, "Defaults", "id").doc shouldBe "The unique identifier."
            }

            scenario("a deprecated property is marked, not dropped") {
                field(SPEC_30, "Defaults", "legacy").deprecated shouldBe true
                field(SPEC_30, "Defaults", "id").deprecated shouldBe false
            }
        }

        feature("formats with a real Kotlin type") {
            scenario("uuid and date stop being strings") {
                field(SPEC_30, "Defaults", "ref").type shouldBe TypeRef.UuidRef
                field(SPEC_30, "Defaults", "day").type shouldBe TypeRef.LocalDateRef
            }
        }

        feature("open-ended objects") {
            scenario("additionalProperties with a schema types the values") {
                field(SPEC_31, "Shapes", "counts").type shouldBe TypeRef.MapRef(TypeRef.LongRef)
            }

            scenario("an object with neither properties nor a value schema stays raw JSON") {
                field(SPEC_31, "Shapes", "anything").type shouldBe TypeRef.JsonObjectRef
            }
        }
    })
