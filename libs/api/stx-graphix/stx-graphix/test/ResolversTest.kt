package com.softistx.graphix

import com.softistx.graphix.fixture.CounterResolvers
import com.softistx.graphix.fixture.ExtendedScalarQueries
import com.softistx.graphix.fixture.NotAResolver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ResolversTest :
    FeatureSpec({
        feature("one registration, routed by annotation") {
            scenario("a class holding a query and a mutation is registered once") {
                val graphix = Graphix { resolvers(CounterResolvers()) }
                graphix.execute(GraphixRequest("mutation { bump(by: 3) }")).data shouldBe mapOf("bump" to 3)
                graphix.execute(GraphixRequest("{ count }")).data shouldBe mapOf("count" to 3)
            }

            scenario("several instances go in one call") {
                val graphix = Graphix { resolvers(CounterResolvers(), ExtendedScalarQueries()) }
                val result = graphix.execute(GraphixRequest("""{ count date(value: "2026-09-02") }"""))
                result.data shouldBe mapOf("count" to 0, "date" to "2026-09-02")
            }

            scenario("a collection registers the same way — Spring hands over a bean list") {
                val graphix = Graphix { resolvers(listOf(CounterResolvers(), ExtendedScalarQueries())) }
                graphix.execute(GraphixRequest("{ count }")).data shouldBe mapOf("count" to 0)
            }

            scenario("a schema with no mutation root has none, rather than failing to build") {
                val graphix = Graphix { resolvers(ExtendedScalarQueries()) }
                graphix.sdl() shouldContain "type Query"
                graphix.execute(GraphixRequest("mutation { bump(by: 1) }")).isOk shouldBe false
            }
        }

        feature("what registration refuses") {
            scenario("a class carrying no mapping is a build failure naming it") {
                val failure = shouldThrow<GraphixException> { Graphix { resolvers(NotAResolver()) } }
                failure.message shouldContain "NotAResolver"
                failure.message shouldContain "@QueryMapping"
            }

            scenario("registering nothing at all is still no query root") {
                shouldThrow<GraphixException> { Graphix { } }
            }
        }
    })
