package com.strange.dgs.plugin

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GenerateDgsTest :
    FeatureSpec({

        fun settings(
            schemaPaths: List<Path> = emptyList(),
            packageName: String = "com.example.codegen",
            typeMapping: Map<String, String> = emptyMap(),
        ) = object : DgsCodegenSettings {
            override val schemaPaths: List<Path> = schemaPaths
            override val packageName: String = packageName
            override val language: DgsLanguage = DgsLanguage.Kotlin
            override val generateClient: Boolean = false
            override val generateDataTypes: Boolean = true
            override val generateInterfaces: Boolean = false
            override val generateCustomAnnotations: Boolean = false
            override val typeMapping: Map<String, String> = typeMapping
            override val includeImports: Map<String, String> = emptyMap()
        }

        fun Path.writeSchema() {
            createDirectories()
            (this / "schema.graphqls").writeText(
                """
                type Query {
                  book(id: String!): Book
                }
                type Book {
                  id: String!
                  title: String!
                }
                input CreateBookInput {
                  title: String!
                }
                """.trimIndent(),
            )
        }

        feature("what is refused before a byte is written") {
            scenario("no schema files is a mistake, not a no-op") {
                val error =
                    shouldThrow<IllegalArgumentException> {
                        settings().validate(emptySet())
                    }

                error.message shouldContain "no schema files found"
            }

            scenario("a blank packageName") {
                val error =
                    shouldThrow<IllegalArgumentException> {
                        settings(packageName = " ").validate(emptySet())
                    }

                error.message shouldContain "packageName must not be blank"
            }
        }

        feature("schema discovery") {
            scenario("empty schemaPaths walks resources/graphql under the module") {
                val module = createTempDirectory("dgs-module")
                (module / "resources" / "graphql").writeSchema()

                val files = resolveSchemaFiles(emptyList(), module)

                files.map { it.name }.toSet() shouldBe setOf("schema.graphqls")
            }

            scenario("a nested .gqls file is included, matching Graphix's split SDL") {
                val module = createTempDirectory("dgs-module")
                val graphql = module / "resources" / "graphql"
                graphql.createDirectories()
                (graphql / "schema.graphqls").writeText("type Query { ping: String }")
                val nested = graphql / "nested"
                nested.createDirectories()
                (nested / "extra.gqls").writeText("extend type Query { pong: String }")

                val files = resolveSchemaFiles(emptyList(), module)

                files.map { it.name }.toSet() shouldBe setOf("schema.graphqls", "extra.gqls")
            }

            scenario("an operation .graphql next to the schema is not SDL") {
                val module = createTempDirectory("dgs-module")
                val graphql = module / "resources" / "graphql"
                graphql.writeSchema()
                val documents = graphql / "documents"
                documents.createDirectories()
                (documents / "Products.graphql").writeText("query Products { products { id } }")

                val files = resolveSchemaFiles(emptyList(), module)

                files.map { it.name }.toSet() shouldBe setOf("schema.graphqls")
            }
        }

        feature("generation") {
            scenario("Kotlin types and DgsConstants land under packageName") {
                val module = createTempDirectory("dgs-module")
                val graphql = module / "resources" / "graphql"
                graphql.writeSchema()
                val output = createTempDirectory("dgs-out")

                generateDgs(settings(), module, output)

                val types = output / "com" / "example" / "codegen" / "types"
                (types / "Book.kt").exists() shouldBe true
                (types / "CreateBookInput.kt").exists() shouldBe true
                (output / "com" / "example" / "codegen" / "DgsConstants.kt").exists() shouldBe true
                (types / "CreateBookInput.kt").readText() shouldContain "title"
            }

            scenario("typeMapping skips generating that GraphQL type") {
                val module = createTempDirectory("dgs-module")
                (module / "resources" / "graphql").writeSchema()
                val output = createTempDirectory("dgs-out")

                generateDgs(
                    settings(typeMapping = mapOf("Book" to "com.example.Book")),
                    module,
                    output,
                )

                (output / "com" / "example" / "codegen" / "types" / "Book.kt").exists() shouldBe false
                (output / "com" / "example" / "codegen" / "types" / "CreateBookInput.kt").exists() shouldBe true
            }
        }
    })
