package com.softistx.openapi.parser

import com.softistx.openapi.EnumType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun spec(status: String) =
    """
openapi: 3.0.1
info: { title: T, version: 1.0.0 }
paths:
  /a: { get: { tags: [t], operationId: a, responses: { '200': { description: ok } } } }
components:
  schemas:
    Status:
${status.prependIndent("      ")}
    """.trimIndent()

private fun enum(status: String) =
    OpenApiParser()
        .parse(spec(status))
        .models
        .filterIsInstance<EnumType>()
        .single()

/**
 * Deriving an entry name from a wire value is a guess, and the document often knows better —
 * `x-enum-varnames` is what NSwag and openapi-generator already write for exactly this. It is also
 * the only escape from a collision between two values that derive one name, which was a dead end.
 */
class EnumNamingTest :
    FeatureSpec({

        feature("names the document supplies") {
            scenario("they replace the derived ones, in enum order") {
                enum(
                    """
                    type: string
                    enum: [a, b]
                    x-enum-varnames: [ALPHA, BETA]
                    """.trimIndent(),
                ).entries.map { it.name } shouldContainExactly listOf("ALPHA", "BETA")
            }

            scenario("NSwag's spelling means the same thing") {
                enum(
                    """
                    type: string
                    enum: [a, b]
                    x-enumNames: [ALPHA, BETA]
                    """.trimIndent(),
                ).entries.map { it.name } shouldContainExactly listOf("ALPHA", "BETA")
            }

            scenario("the wire values are untouched by any of it") {
                enum(
                    """
                    type: string
                    enum: [a, b]
                    x-enum-varnames: [ALPHA, BETA]
                    """.trimIndent(),
                ).entries.map { it.wireValue } shouldContainExactly listOf("a", "b")
            }

            scenario("they rescue two values that would derive one name") {
                // Without this the parse fails outright, and the document may not be ours to change.
                enum(
                    """
                    type: string
                    enum: [in-progress, in_progress]
                    x-enum-varnames: [IN_PROGRESS, IN_PROGRESS_LEGACY]
                    """.trimIndent(),
                ).entries.map { it.name } shouldContainExactly listOf("IN_PROGRESS", "IN_PROGRESS_LEGACY")
            }
        }

        feature("descriptions the document supplies") {
            scenario("each entry keeps its own line") {
                val entries =
                    enum(
                        """
                        type: string
                        enum: [a, b]
                        x-enum-descriptions: ['The first one.', '  ']
                        """.trimIndent(),
                    ).entries

                entries[0].doc shouldBe "The first one."
                entries[1].doc shouldBe null
            }
        }

        feature("lists that do not line up") {
            scenario("a wrong length fails, naming both counts") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        enum(
                            """
                            type: string
                            enum: [a, b, c]
                            x-enum-varnames: [ALPHA, BETA]
                            """.trimIndent(),
                        )
                    }

                error.message shouldContain "2 entries"
                error.message shouldContain "lists 3"
            }

            scenario("two spellings that disagree fail rather than one winning") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        enum(
                            """
                            type: string
                            enum: [a, b]
                            x-enum-varnames: [ALPHA, BETA]
                            x-enumNames: [FIRST, SECOND]
                            """.trimIndent(),
                        )
                    }

                error.message shouldContain "x-enum-varnames"
                error.message shouldContain "x-enumNames"
            }

            scenario("a name that is not an identifier fails, quoting it") {
                val error =
                    shouldThrow<OpenApiParseException> {
                        enum(
                            """
                            type: string
                            enum: [a, b]
                            x-enum-varnames: [ALPHA, 'BETA GAMMA']
                            """.trimIndent(),
                        )
                    }

                error.message shouldContain "BETA GAMMA"
                error.message shouldContain "identifier"
            }
        }
    })
