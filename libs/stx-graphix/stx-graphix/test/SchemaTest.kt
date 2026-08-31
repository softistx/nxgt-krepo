package com.strange.graphix

import com.strange.graphix.fixture.BadQueries
import com.strange.graphix.fixture.GreetingQueries
import com.strange.graphix.fixture.ProductQueries
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SchemaTest :
    FeatureSpec({
        feature("schema from annotations") {
            scenario("query functions become fields, and @Serializable types become objects") {
                val sdl = Graphix { query(ProductQueries()) }.sdl()
                sdl shouldContain "type Query"
                sdl shouldContain "product(id: String!): Product"
                sdl shouldContain "type Product"
                sdl shouldContain "name: String!"
                sdl shouldContain "tags: [String!]!"
            }

            scenario("a @GraphQLIgnore property is not a GraphQL field") {
                val sdl = Graphix { query(ProductQueries()) }.sdl()
                sdl shouldNotContain "secret"
            }

            scenario("@QueryMapping(name) renames a root field") {
                val sdl = Graphix { query(GreetingQueries()) }.sdl()
                sdl shouldContain "shout"
                sdl shouldNotContain "loud"
            }

            scenario("enums and lists round-trip through SerialDescriptor") {
                val sdl = Graphix { query(ProductQueries()) }.sdl()
                sdl shouldContain "enum Size"
                sdl shouldContain "sizes: [Size!]!"
            }

            scenario("a type that is not @Serializable fails naming that type") {
                val failure = shouldThrow<GraphixException> { Graphix { query(BadQueries()) } }
                failure.message shouldContain "NotSerializable"
                failure.message shouldContain "not @Serializable"
            }

            scenario("no query root is a schema-build failure") {
                shouldThrow<GraphixException> { Graphix { } }
            }

            scenario("a GraphQL argument must be @Argument") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(
                                object {
                                    @com.strange.graphix.schema.QueryMapping
                                    fun product(id: String): String = id
                                },
                            )
                        }
                    }
                failure.message shouldContain "must be @Argument"
            }

            scenario("an input-object field must not be @Argument") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(
                                com.strange.graphix.fixture
                                    .BadInputQueries(),
                            )
                        }
                    }
                failure.message shouldContain "must not be @Argument"
                failure.message shouldContain "name"
            }
        }

        feature("introspection") {
            scenario("{ __schema } names the query type") {
                val result =
                    Graphix { query(GreetingQueries()) }
                        .execute(GraphixRequest("{ __schema { queryType { name } } }"))
                result.isOk shouldBe true
                ((result.data.shouldNotBeNull()["__schema"] as Map<*, *>)["queryType"] as Map<*, *>)["name"] shouldBe "Query"
            }

            scenario("{ __type } describes an annotated object") {
                val result =
                    Graphix { query(ProductQueries()) }
                        .execute(GraphixRequest("""{ __type(name: "Product") { name fields { name } } }"""))
                result.isOk shouldBe true
                val type = result.data.shouldNotBeNull()["__type"] as Map<*, *>
                type["name"] shouldBe "Product"
                (type["fields"] as List<*>).map { (it as Map<*, *>)["name"] } shouldContain "name"
            }

            scenario("the GraphQL introspection query completes") {
                val result =
                    Graphix { query(ProductQueries()) }
                        .execute(GraphixRequest(graphql.introspection.IntrospectionQuery.INTROSPECTION_QUERY))
                result.isOk shouldBe true
                result.data.shouldNotBeNull().containsKey("__schema") shouldBe true
            }
        }
    })
