package com.strange.graphql

import com.strange.graphql.fixture.BoomQueries
import com.strange.graphql.fixture.Caller
import com.strange.graphql.fixture.ContextQueries
import com.strange.graphql.fixture.GreetingQueries
import com.strange.graphql.fixture.Product
import com.strange.graphql.fixture.ProductMutations
import com.strange.graphql.fixture.ProductQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay

class ExecuteTest :
    FeatureSpec({
        feature("execute") {
            scenario("a query field returns its value") {
                val graphql = Graphix { query(GreetingQueries()) }
                val result = graphql.execute(GraphixRequest("{ hello }"))
                result.isOk shouldBe true
                result.data shouldBe mapOf("hello" to "world")
            }

            scenario("arguments bind, and a Kotlin default is used when the argument is omitted") {
                val graphql = Graphix { query(GreetingQueries()) }
                graphql.execute(GraphixRequest("""{ shout(name: "ada") }""")).data shouldBe mapOf("shout" to "ADA")
                graphql.execute(GraphixRequest("{ shout }")).data shouldBe mapOf("shout" to "STRANGER")
            }

            scenario("a nested @Serializable object is fetched through its properties") {
                val graphql = Graphix { query(ProductQueries()) }
                val result = graphql.execute(GraphixRequest("""{ product(id: "p1") { name tags } }"""))
                result.isOk shouldBe true
                val product = (result.data?.get("product") as Map<*, *>).shouldNotBeNull()
                product["name"] shouldBe "Mug"
                product["tags"] shouldBe listOf("kitchen")
            }

            scenario("a mutation writes and the next query reads it") {
                val products = mutableListOf(Product("p1", "Mug"))
                val graphql =
                    Graphix {
                        query(ProductQueries(products))
                        mutation(ProductMutations(products))
                    }
                val created =
                    graphql.execute(
                        GraphixRequest(
                            """mutation { createProduct(input: { name: "Kettle" }) { id name } }""",
                        ),
                    )
                created.isOk shouldBe true
                (created.data?.get("createProduct") as Map<*, *>)["name"] shouldBe "Kettle"
                val listed = graphql.execute(GraphixRequest("{ products { name } }"))
                val names = (listed.data?.get("products") as List<*>).map { (it as Map<*, *>)["name"] }
                names shouldBe listOf("Mug", "Kettle")
            }

            scenario("a suspend resolver may delay and still return") {
                val graphql =
                    Graphix {
                        query(
                            object {
                                @com.strange.graphql.schema.Query
                                suspend fun later(): String {
                                    delay(10)
                                    return "ok"
                                }
                            },
                        )
                    }
                graphql.execute(GraphixRequest("{ later }")).data shouldBe mapOf("later" to "ok")
            }

            scenario("a thrown resolver becomes a GraphQL error, not an execute exception") {
                val result = Graphix { query(BoomQueries()) }.execute(GraphixRequest("{ boom }"))
                result.isOk shouldBe false
                result.errors.single().message shouldContain "nope"
            }

            scenario("@GraphQLContext is taken from the execute context, not from arguments") {
                val graphql = Graphix { query(ContextQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest("{ who }"),
                        context = mapOf(Caller::class to Caller("fr")),
                    )
                result.data shouldBe mapOf("who" to "fr")
            }
        }
    })
