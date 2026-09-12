package com.softistx.graphix

import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.ProductQueries
import com.softistx.graphix.validation.GraphixLimits
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ValidationTest :
    FeatureSpec({
        feature("complexity limits") {
            scenario("graphql-java 26's defaults still let a one-field query through") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val result = graphql.execute(GraphixRequest("{ hello }"))
                result.isOk shouldBe true
                result.data shouldBe mapOf("hello" to "world")
            }

            scenario("maxDepth declared on the engine rejects a nested selection") {
                val graphql =
                    Graphix {
                        resolvers(ProductQueries())
                        validation { maxDepth = 1 }
                    }
                val result = graphql.execute(GraphixRequest("""{ product(id: "p1") { name } }"""))
                result.isOk shouldBe false
                result.errors.single().message shouldContain "depth"
            }

            scenario("a per-operation GraphixLimits overrides the engine") {
                val graphql =
                    Graphix {
                        resolvers(ProductQueries())
                        validation { maxDepth = 1 }
                    }
                val allowed =
                    graphql.execute(
                        GraphixRequest("""{ product(id: "p1") { name } }"""),
                        context = mapOf(GraphixLimits::class to GraphixLimits(maxDepth = 8)),
                    )
                allowed.isOk shouldBe true
            }

            scenario("maxFields declared on the engine rejects a two-field selection") {
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries())
                        validation { maxFields = 1 }
                    }
                val result = graphql.execute(GraphixRequest("""{ hello shout(name: "ada") }"""))
                result.isOk shouldBe false
                result.errors.single().message shouldContain "fields"
            }
        }

        feature("field rules") {
            scenario("a complaint on a coerced argument aborts before the resolver") {
                val graphql =
                    Graphix {
                        resolvers(GreetingQueries())
                        validation {
                            field("/shout") {
                                val name = argument("name") as? String
                                "name is too long".takeIf { name != null && name.length > 3 }
                            }
                        }
                    }
                val refused = graphql.execute(GraphixRequest("""{ shout(name: "abcd") }"""))
                refused.isOk shouldBe false
                refused.errors.single().message shouldContain "too long"
                val ok = graphql.execute(GraphixRequest("""{ shout(name: "ada") }"""))
                ok.isOk shouldBe true
                ok.data shouldBe mapOf("shout" to "ADA")
            }
        }
    })
