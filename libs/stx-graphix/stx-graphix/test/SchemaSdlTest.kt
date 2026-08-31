package com.strange.graphix

import com.strange.graphix.fixture.BookFields
import com.strange.graphix.fixture.BookQueries
import com.strange.graphix.fixture.GreetingQueries
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class SchemaSdlTest :
    FeatureSpec({
        feature("schema resources") {
            scenario("several files under a directory merge, including nested .gqls") {
                val graphql =
                    Graphix {
                        schemaLocations("classpath:graphix-sdl/")
                        query(GreetingQueries())
                    }
                graphql.sdl() shouldContain "hello: String!"
                graphql.sdl() shouldContain "shout(name: String): String"
                graphql.sdl() shouldContain "type Book"
                graphql.sdl() shouldContain "type Author"
            }

            scenario("{ __schema } works on an SDL schema") {
                val result =
                    Graphix {
                        schemaLocations("classpath:graphix-sdl/")
                        query(GreetingQueries())
                    }.execute(GraphixRequest("{ __schema { queryType { name } types { name } } }"))
                result.isOk shouldBe true
                val schema = result.data.shouldNotBeNull()["__schema"] as Map<*, *>
                (schema["queryType"] as Map<*, *>)["name"] shouldBe "Query"
                (schema["types"] as List<*>).map { (it as Map<*, *>)["name"] } shouldContain "Book"
            }

            scenario("annotated resolvers run against the SDL schema") {
                val graphql =
                    Graphix {
                        schemaLocations("classpath:graphix-sdl/")
                        query(GreetingQueries())
                    }
                val hello = graphql.execute(GraphixRequest("{ hello }"))
                hello.errors.shouldBe(emptyList())
                hello.data shouldBe mapOf("hello" to "world")
                val shout = graphql.execute(GraphixRequest("""{ shout(name: "ada") }"""))
                shout.data shouldBe mapOf("shout" to "ADA")
            }

            scenario("SchemaMapping loaders still wire onto SDL types") {
                val fields = BookFields()
                val graphql =
                    Graphix {
                        schemaLocations("classpath:graphix-sdl/")
                        query(BookQueries())
                        type(fields)
                    }
                val one = graphql.execute(GraphixRequest("{ book { title author { name } } }"))
                one.errors.shouldBe(emptyList())
                ((one.data.shouldNotBeNull()["book"] as Map<*, *>)["author"] as Map<*, *>)["name"] shouldBe "Frank"
                fields.authorLoads.get() shouldBe 1
            }

            scenario("a missing directory after an explicit location keeps the annotated schema") {
                val graphql =
                    Graphix {
                        schemaLocations("classpath:does-not-exist-sdl/")
                        query(GreetingQueries())
                    }
                graphql.execute(GraphixRequest("{ hello }")).data shouldBe mapOf("hello" to "world")
            }

            scenario("an unreadable document fails schema build naming the file") {
                val dir = createTempDirectory("graphix-sdl")
                dir.resolve("bad.graphqls").writeText("type Query {")
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            schemaLocations("file:${dir.toAbsolutePath()}")
                            query(GreetingQueries())
                        }
                    }
                failure.message shouldContain "bad.graphqls"
            }
        }
    })
