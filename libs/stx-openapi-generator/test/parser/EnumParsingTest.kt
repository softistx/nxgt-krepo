package com.softistx.openapi.parser

import com.softistx.openapi.EnumType
import com.softistx.openapi.ObjectType
import com.softistx.openapi.TypeRef
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(schemas: String) =
    """
    |openapi: 3.0.1
    |info: {title: t, version: "1"}
    |paths:
    |  /a: {get: {tags: [t], operationId: a, responses: {'200': {description: ok}}}}
    |components:
    |  schemas:
    |$schemas
    """.trimMargin()

private val SPEC =
    spec(
        """
        |    Status:
        |      type: string
        |      enum: [active, in-progress, ARCHIVED]
        |    Code:
        |      type: integer
        |      enum: [400, 404]
        |    Weird:
        |      type: string
        |      enum: ["2xx", ""]
        |    Order:
        |      type: object
        |      properties:
        |        status: {${'$'}ref: '#/components/schemas/Status'}
        """.trimMargin(),
    )

private fun enums(document: String = SPEC) = OpenApiParser().parse(document).models.filterIsInstance<EnumType>()

private fun enum(name: String) = enums().first { it.name == name }

/**
 * A constrained schema is a declaration, not a `String`. Before this it was dropped: the schema had
 * no `properties`, so nothing was emitted and every `$ref` to it resolved to the underlying scalar.
 */
class EnumParsingTest :
    FeatureSpec({

        feature("what becomes an enum") {
            scenario("a string enum keeps its component name and its values") {
                enum("Status").let {
                    it.base shouldBe TypeRef.StringRef
                    it.entries.map { entry -> entry.name } shouldContainExactly
                        listOf("ACTIVE", "IN_PROGRESS", "ARCHIVED")
                    it.entries.map { entry -> entry.wireValue } shouldContainExactly
                        listOf("active", "in-progress", "ARCHIVED")
                }
            }

            scenario("an integer enum carries its numbers") {
                enum("Code").let {
                    it.base shouldBe TypeRef.IntRef
                    it.entries.map { entry -> entry.wireValue } shouldContainExactly listOf("400", "404")
                }
            }

            scenario("a ref to an enum names the enum instead of resolving to its scalar") {
                val order =
                    OpenApiParser()
                        .parse(SPEC)
                        .models
                        .filterIsInstance<ObjectType>()
                        .first { it.name == "Order" }
                order.fields.single().type shouldBe TypeRef.ModelRef("Status")
            }

            scenario("an enum this generator cannot express stays its underlying scalar") {
                // A float enum has no sane Kotlin enum; the property keeps working as a Double.
                val document =
                    spec(
                        """
                        |    Ratio: {type: number, enum: [0.5, 1.5]}
                        |    Holder: {type: object, properties: {ratio: {${'$'}ref: '#/components/schemas/Ratio'}}}
                        """.trimMargin(),
                    )
                val parsed = OpenApiParser().parse(document)
                parsed.models.filterIsInstance<EnumType>() shouldContainExactly emptyList()
                parsed.models
                    .filterIsInstance<ObjectType>()
                    .single()
                    .fields
                    .single()
                    .type shouldBe TypeRef.DoubleRef
            }
        }

        feature("names a document is under no obligation to make legal") {
            scenario("values that are not identifiers still become entries") {
                enum("Weird").entries.map { it.name } shouldContainExactly listOf("V2XX", "EMPTY")
            }

            scenario("two values that would become one entry fail, naming both") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        OpenApiParser().parse(
                            spec("""|    S: {type: string, enum: [in-progress, in_progress]}""".trimMargin()),
                        )
                    }

                error.message shouldContain "IN_PROGRESS"
                error.message shouldContain "'in-progress'"
                error.message shouldContain "'in_progress'"
            }
        }

        feature("the fallback entry") {
            scenario("it is added to every enum, with a wire value no server accepts") {
                enum("Status").fallback.let {
                    it.name shouldBe "UNKNOWN"
                    it.wireValue shouldBe "__unknown__"
                }
                enum("Code").fallback.wireValue shouldBe "${Int.MIN_VALUE}"
            }

            scenario("a document that uses the fallback's name keeps it, and the fallback moves") {
                val parsed = OpenApiParser().parse(spec("""|    S: {type: string, enum: [unknown, other]}""".trimMargin()))
                val enum = parsed.models.filterIsInstance<EnumType>().single()

                enum.entries.map { it.name } shouldContainExactly listOf("UNKNOWN", "OTHER")
                enum.fallback.name shouldBe "UNKNOWN_"
            }
        }
    })
