package com.strange.graphix

import com.strange.graphix.fixture.BadBatchFields
import com.strange.graphix.fixture.BothMappings
import com.strange.graphix.fixture.DuplicateNameFields
import com.strange.graphix.fixture.NamedSchemaFields
import com.strange.graphix.fixture.Product
import com.strange.graphix.fixture.ProductFields
import com.strange.graphix.fixture.ProductQueries
import com.strange.graphix.fixture.Review
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class TypeFieldTest :
    FeatureSpec({
        feature("schema") {
            scenario("@SchemaMapping adds a field on the parent type") {
                val sdl =
                    Graphix {
                        query(ProductQueries())
                        type(ProductFields())
                    }.sdl()
                sdl shouldContain "type Product"
                sdl shouldContain "extra: String!"
                sdl shouldContain "tagged(prefix: String): String!"
                sdl shouldContain "reviews: [Review!]!"
            }

            scenario("a @SchemaMapping that collides with a property fails schema build") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(ProductQueries())
                            type(DuplicateNameFields())
                        }
                    }
                failure.message shouldContain "duplicate field 'name'"
            }

            scenario("@SchemaMapping typeName and field override the defaults") {
                val sdl =
                    Graphix {
                        query(ProductQueries())
                        type(NamedSchemaFields())
                    }.sdl()
                sdl shouldContain "nick: String!"
                sdl shouldNotContain "unused"
            }

            scenario("@SchemaMapping and @BatchMapping cannot both sit on the same function") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(ProductQueries())
                            type(BothMappings())
                        }
                    }
                failure.message shouldContain "cannot both sit"
            }

            scenario("@BatchMapping cannot take GraphQL arguments") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(ProductQueries())
                            type(BadBatchFields())
                        }
                    }
                failure.message shouldContain "cannot have GraphQL arguments"
            }
        }

        feature("execute") {
            scenario("@SchemaMapping reads the parent and binds arguments") {
                val graphql =
                    Graphix {
                        query(ProductQueries())
                        type(ProductFields())
                    }
                val extra = graphql.execute(GraphixRequest("""{ product(id: "p1") { extra } }"""))
                (extra.data.shouldNotBeNull()["product"] as Map<*, *>)["extra"] shouldBe "MUG"
                val tagged = graphql.execute(GraphixRequest("""{ product(id: "p1") { tagged(prefix: "y") } }"""))
                (tagged.data.shouldNotBeNull()["product"] as Map<*, *>)["tagged"] shouldBe "y-Mug"
                val defaulted = graphql.execute(GraphixRequest("""{ product(id: "p1") { tagged } }"""))
                (defaulted.data.shouldNotBeNull()["product"] as Map<*, *>)["tagged"] shouldBe "x-Mug"
            }

            scenario("@BatchMapping loads once for a list of parents") {
                val products = mutableListOf(Product("p1", "Mug"), Product("p2", "Kettle"))
                val fields =
                    ProductFields(
                        reviews =
                            mapOf(
                                "p1" to listOf(Review("r1", "Nice mug")),
                                "p2" to listOf(Review("r2", "Loud")),
                            ),
                    )
                val graphql =
                    Graphix {
                        query(ProductQueries(products))
                        type(fields)
                    }
                val result = graphql.execute(GraphixRequest("{ products { name reviews { body } } }"))
                result.isOk shouldBe true
                fields.loads.get() shouldBe 1
                val listed = (result.data?.get("products") as List<*>).shouldNotBeNull()
                listed.size shouldBe 2
                ((listed[0] as Map<*, *>)["reviews"] as List<*>).size shouldBe 1
                ((listed[1] as Map<*, *>)["reviews"] as List<*>).size shouldBe 1
            }
        }
    })
