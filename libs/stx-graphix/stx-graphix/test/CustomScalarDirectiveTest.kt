package com.softistx.graphix

import com.softistx.graphix.fixture.Caller
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.scalar.scalar
import com.softistx.graphix.schema.Directive
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.fieldDirective
import graphql.language.StringValue
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

data class Money(
    val cents: Long,
)

class MoneyQueries {
    @QueryMapping
    fun price(): Money = Money(199)
}

class UpperQueries {
    @QueryMapping
    @Directive("uppercase")
    fun hello(): String = "world"
}

class CustomScalarDirectiveTest :
    FeatureSpec({
        feature("custom scalars") {
            scenario("a declared scalar serializes through GraphQLContext") {
                val graphql =
                    Graphix {
                        scalar("Money", kotlinType = Money::class) {
                            serialize { value -> (value as Money).cents.toString() }
                            parseValue { input -> Money((input as String).toLong()) }
                            parseLiteral { input ->
                                val literal = input as StringValue
                                Money(requireNotNull(literal.value).toLong())
                            }
                        }
                        query(MoneyQueries())
                    }
                graphql.sdl() shouldContain "scalar Money"
                val result = graphql.execute(GraphixRequest("{ price }"))
                result.isOk shouldBe true
                result.data shouldBe mapOf("price" to "199")
            }

            scenario("serialize sees execute context") {
                val graphql =
                    Graphix {
                        scalar("Money", kotlinType = Money::class) {
                            serialize { value ->
                                val tag = get<Caller>(Caller::class)?.locale ?: ""
                                "${(value as Money).cents}$tag"
                            }
                            parseValue { input -> Money((input as String).toLong()) }
                        }
                        query(MoneyQueries())
                    }
                val result =
                    graphql.execute(
                        GraphixRequest("{ price }"),
                        context = mapOf(Caller::class to Caller("USD")),
                    )
                result.data shouldBe mapOf("price" to "199USD")
            }
        }

        feature("field directives") {
            scenario("@Directive wraps a mapping with proceed()") {
                val graphql =
                    Graphix {
                        fieldDirective("uppercase") {
                            val value = proceed()
                            (value as? String)?.uppercase() ?: value
                        }
                        query(UpperQueries())
                    }
                val result = graphql.execute(GraphixRequest("{ hello }"))
                result.isOk shouldBe true
                result.data shouldBe mapOf("hello" to "WORLD")
            }

            scenario("GraphixCustomizer and engine() still execute") {
                val graphql =
                    Graphix {
                        query(GreetingQueries())
                        customize(GraphixCustomizer { })
                        engine { }
                    }
                graphql.execute(GraphixRequest("{ hello }")).data shouldBe mapOf("hello" to "world")
            }
        }
    })
