package com.strange.apollo.plugin

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

class GenerateApolloTest :
    FeatureSpec({

        fun settings(
            schemaPaths: List<Path> = emptyList(),
            srcDir: List<Path> = emptyList(),
            packageName: String = "com.example.apollo",
            generateDataBuilders: Boolean = false,
            generateOptionalOperationVariables: Boolean = false,
        ) = object : ApolloSettings {
            override val schemaPaths: List<Path> = schemaPaths
            override val srcDir: List<Path> = srcDir
            override val packageName: String = packageName
            override val mapScalar: Map<String, String> = emptyMap()
            override val mapScalarAdapters: Map<String, String> = emptyMap()
            override val generateDataBuilders: Boolean = generateDataBuilders
            override val generateFragmentImplementations: Boolean = false
            override val generateOptionalOperationVariables: Boolean = generateOptionalOperationVariables
            override val useSemanticNaming: Boolean = true
        }

        fun Path.writeShop() {
            val graphql = this / "resources" / "graphql"
            graphql.createDirectories()
            (graphql / "schema.graphqls").writeText(
                """
                type Query {
                  product(id: String!): Product
                }
                type Product {
                  id: String!
                  name: String!
                }
                """.trimIndent(),
            )
            val documents = graphql / "documents"
            documents.createDirectories()
            (documents / "Product.graphql").writeText(
                """
                query ProductById(${'$'}id: String!) {
                  product(id: ${'$'}id) {
                    id
                    name
                  }
                }
                """.trimIndent(),
            )
        }

        feature("what is refused before a byte is written") {
            scenario("no schema files") {
                val error =
                    shouldThrow<IllegalArgumentException> {
                        settings().validate(emptyList(), emptyList())
                    }
                error.message shouldContain "no schema files found"
            }

            scenario("no operation documents") {
                val error =
                    shouldThrow<IllegalArgumentException> {
                        settings().validate(listOf(createTempDirectory("schema").toFile()), emptyList())
                    }
                error.message shouldContain "no operation documents found"
            }

            scenario("a blank packageName") {
                val error =
                    shouldThrow<IllegalArgumentException> {
                        settings(packageName = " ").validate(emptyList(), emptyList())
                    }
                error.message shouldContain "packageName must not be blank"
            }
        }

        feature("schema and document discovery") {
            scenario("defaults walk resources/graphql and resources/graphql/documents") {
                val module = createTempDirectory("apollo-module")
                module.writeShop()

                resolveSchemaFiles(emptyList(), module).map { it.name }.toSet() shouldBe
                    setOf("schema.graphqls")
                resolveDocumentFiles(emptyList(), module).map { it.name }.toSet() shouldBe
                    setOf("Product.graphql")
            }

            scenario("a .graphql next to the schema is not treated as SDL") {
                val module = createTempDirectory("apollo-module")
                module.writeShop()
                (module / "resources" / "graphql" / "orphan.graphql").writeText("query X { __typename }")

                resolveSchemaFiles(emptyList(), module).map { it.name }.toSet() shouldBe
                    setOf("schema.graphqls")
            }
        }

        feature("generation") {
            scenario("the operation class carries OPERATION_DOCUMENT") {
                val module = createTempDirectory("apollo-module")
                module.writeShop()
                val output = createTempDirectory("apollo-out")

                generateApollo(settings(), module, output)

                val generated = output / "com" / "example" / "apollo" / "ProductByIdQuery.kt"
                generated.exists() shouldBe true
                generated.readText() shouldContain "OPERATION_DOCUMENT"
                generated.readText() shouldContain "product(id:"
            }

            scenario("generateDataBuilders writes a builder beside the models") {
                val module = createTempDirectory("apollo-module")
                module.writeShop()
                val output = createTempDirectory("apollo-out")

                generateApollo(settings(generateDataBuilders = true), module, output)

                (output / "com" / "example" / "apollo" / "ProductByIdQuery.kt").exists() shouldBe true
                output.toFile().walkTopDown().any { it.name.contains("Builder") || it.name.contains("DataBuilder") } shouldBe true
            }
        }
    })
