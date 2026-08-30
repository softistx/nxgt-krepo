package com.strange.graphix

import com.strange.graphix.fixture.LoadedQueries
import com.strange.graphix.fixture.MissingLoadQueries
import com.strange.graphix.fixture.ProductLoaders
import com.strange.graphix.fixture.ReviewFields
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class LoaderTest :
    FeatureSpec({
        feature("schema") {
            scenario("@Loader is a named DataLoader, @Load is not a GraphQL argument") {
                val sdl =
                    Graphix {
                        query(LoadedQueries())
                        type(ReviewFields())
                        loader(ProductLoaders())
                    }.sdl()
                sdl shouldContain "product(id: String!): Product"
                sdl shouldContain "reviews: [Review!]!"
                sdl shouldNotContain "loaded"
            }

            scenario("an unknown @Load name fails schema build") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix { query(MissingLoadQueries()) }
                    }
                failure.message shouldContain "@Load 'nope'"
            }
        }

        feature("execute") {
            scenario("@Query @Load batches by argument") {
                val loaders = ProductLoaders()
                val graphql =
                    Graphix {
                        query(LoadedQueries())
                        loader(loaders)
                    }
                val one = graphql.execute(GraphixRequest("""{ product(id: "p1") { name } }"""))
                (one.data.shouldNotBeNull()["product"] as Map<*, *>)["name"] shouldBe "Mug"
                loaders.productLoads.get() shouldBe 1
            }

            scenario("@Field @Load from a parent property batches once for a list") {
                val loaders = ProductLoaders()
                val graphql =
                    Graphix {
                        query(LoadedQueries())
                        type(ReviewFields())
                        loader(loaders)
                    }
                val result = graphql.execute(GraphixRequest("{ products { name reviews { body } } }"))
                result.isOk shouldBe true
                loaders.reviewLoads.get() shouldBe 1
                val listed = (result.data.shouldNotBeNull()["products"] as List<*>)
                listed.size shouldBe 2
                ((listed[0] as Map<*, *>)["reviews"] as List<*>).size shouldBe 1
            }
        }
    })
