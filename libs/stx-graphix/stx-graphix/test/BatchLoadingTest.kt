package com.strange.graphix

import com.strange.graphix.fixture.DfeBatch
import com.strange.graphix.fixture.DfeFields
import com.strange.graphix.fixture.LimitedSnippets
import com.strange.graphix.fixture.Product
import com.strange.graphix.fixture.ProductQueries
import com.strange.graphix.fixture.ReviewBatch
import com.strange.graphix.fixture.SingularBatch
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class BatchLoadingTest :
    FeatureSpec({
        feature("schema") {
            scenario("@BatchMapping registers the field without SchemaMapping") {
                val sdl =
                    Graphix {
                        query(ProductQueries())
                        type(ReviewBatch())
                    }.sdl()
                sdl shouldContain "reviews: [Review!]!"
            }

            scenario("a single parent instead of List<Parent> fails schema build") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(ProductQueries())
                            type(SingularBatch())
                        }
                    }
                failure.message shouldContain "List<T>"
            }
        }

        feature("execute") {
            scenario("@GraphQLContext DataFetchingEnvironment sees source and arguments") {
                val graphql =
                    Graphix {
                        query(ProductQueries())
                        type(DfeFields())
                    }
                val tagged = graphql.execute(GraphixRequest("""{ product(id: "p1") { tagged(prefix: "y") } }"""))
                (tagged.data.shouldNotBeNull()["product"] as Map<*, *>)["tagged"] shouldBe "y-Mug"
            }

            scenario("@BatchMapping can take this field's DataFetchingEnvironment") {
                val batch = DfeBatch()
                val graphql =
                    Graphix {
                        query(ProductQueries())
                        type(batch)
                    }
                graphql.sdl() shouldContain "notes: String!"
                val result = graphql.execute(GraphixRequest("{ products { notes } }"))
                result.isOk shouldBe true
                batch.fieldNames shouldBe listOf("notes")
                val listed = result.data.shouldNotBeNull()["products"] as List<*>
                (listed[0] as Map<*, *>)["notes"] shouldBe "notes"
            }

            scenario("@BatchMapping takes @Argument values and batches when they match") {
                val batch = LimitedSnippets()
                val graphql =
                    Graphix {
                        query(ProductQueries())
                        type(batch)
                    }
                val result = graphql.execute(GraphixRequest("{ products { snippets(limit: 2) } }"))
                result.isOk shouldBe true
                batch.loads.get() shouldBe 1
                val listed = result.data.shouldNotBeNull()["products"] as List<*>
                ((listed[0] as Map<*, *>)["snippets"] as List<*>).size shouldBe 2
            }

            scenario("aliases with different arguments do not share a DataLoader row") {
                val batch = LimitedSnippets()
                val graphql =
                    Graphix {
                        query(ProductQueries())
                        type(batch)
                    }
                val result =
                    graphql.execute(
                        GraphixRequest(
                            """
                            {
                              a: product(id: "p1") { snippets(limit: 1) }
                              b: product(id: "p1") { snippets(limit: 3) }
                            }
                            """.trimIndent(),
                        ),
                    )
                result.isOk shouldBe true
                batch.loads.get() shouldBe 2
                ((result.data.shouldNotBeNull()["a"] as Map<*, *>)["snippets"] as List<*>).size shouldBe 1
                ((result.data.shouldNotBeNull()["b"] as Map<*, *>)["snippets"] as List<*>).size shouldBe 3
            }

            scenario("@BatchMapping keyed by source batches once for a list of parents") {
                val batch = ReviewBatch()
                val graphql =
                    Graphix {
                        query(ProductQueries(mutableListOf(Product("p1", "Mug"), Product("p2", "Kettle"))))
                        type(batch)
                    }
                val result = graphql.execute(GraphixRequest("{ products { name reviews { body } } }"))
                result.isOk shouldBe true
                batch.loads.get() shouldBe 1
                val listed = (result.data.shouldNotBeNull()["products"] as List<*>)
                listed.size shouldBe 2
                ((listed[0] as Map<*, *>)["reviews"] as List<*>).size shouldBe 1
            }
        }
    })
