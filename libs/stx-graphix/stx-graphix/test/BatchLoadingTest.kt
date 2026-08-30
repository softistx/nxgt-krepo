package com.strange.graphix

import com.strange.graphix.fixture.DfeFields
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
