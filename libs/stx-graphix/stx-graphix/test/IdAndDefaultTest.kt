package com.softistx.graphix

import com.softistx.graphix.fixture.BadDefaultQueries
import com.softistx.graphix.fixture.BadIdQueries
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.TicketQueries
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** GraphQL's `ID` scalar, and argument defaults that a client can actually read. */
class IdAndDefaultTest :
    FeatureSpec({
        feature("@GraphQLId") {
            scenario("a String, a Uuid and a list of strings all become ID") {
                val sdl = Graphix { resolvers(TicketQueries()) }.sdl()

                sdl shouldContain "id: ID!"
                sdl shouldContain "batch: ID!"
                sdl shouldContain "tags: [ID!]!"
                sdl shouldContain "title: String!"
            }

            scenario("a resolver's return type and its argument can be ID too") {
                val sdl = Graphix { resolvers(TicketQueries()) }.sdl()

                sdl shouldContain "currentId: ID!"
                sdl shouldContain "ticket(id: ID!)"
            }

            scenario("an ID argument still arrives as a Kotlin String") {
                val graphql = Graphix { resolvers(TicketQueries()) }
                val result = graphql.execute(GraphixRequest("""{ ticket(id: "t7") { id title } }"""))

                result.isOk shouldBe true
                (result.data?.get("ticket") as Map<*, *>)["id"] shouldBe "t7"
            }

            scenario("@GraphQLId on a type that is not a string fails, naming it") {
                val failure = shouldThrow<GraphixException> { Graphix { resolvers(BadIdQueries()) } }

                failure.message shouldContain "@GraphQLId is only for String, Uuid or Long"
            }
        }

        feature("@GraphQLDefault") {
            scenario("an argument keeps its NonNull and advertises the default") {
                val sdl = Graphix { resolvers(TicketQueries()) }.sdl()

                sdl shouldContain "page(limit: Int! = 10)"
            }

            scenario("a Kotlin default alone is still only an optional argument") {
                val sdl = Graphix { resolvers(GreetingQueries()) }.sdl()

                sdl shouldContain "shout(name: String)"
            }

            scenario("the engine supplies the default when the argument is omitted") {
                val graphql = Graphix { resolvers(TicketQueries()) }

                graphql.execute(GraphixRequest("{ page }")).data shouldBe mapOf("page" to 10)
                graphql.execute(GraphixRequest("{ page(limit: 3) }")).data shouldBe mapOf("page" to 3)
            }

            scenario("an input-object field carries its default too") {
                val graphql = Graphix { resolvers(TicketQueries()) }
                val sdl = graphql.sdl()

                sdl shouldContain "limit: Int! = 10"
                sdl shouldContain "sort: String! = \"name\""
                graphql.execute(GraphixRequest("{ paged(input: {}) }")).data shouldBe mapOf("paged" to "name:10")
            }

            scenario("introspection reports the default value") {
                val graphql = Graphix { resolvers(TicketQueries()) }
                val result =
                    graphql.execute(
                        GraphixRequest("""{ __type(name: "Query") { fields { name args { name defaultValue } } } }"""),
                    )

                val fields = ((result.data?.get("__type") as Map<*, *>)["fields"] as List<*>).map { it as Map<*, *> }
                val args = fields.first { it["name"] == "page" }["args"] as List<*>
                (args.first() as Map<*, *>)["defaultValue"] shouldBe "10"
            }

            scenario("a literal that does not parse fails schema build, naming the argument") {
                val failure = shouldThrow<GraphixException> { Graphix { resolvers(BadDefaultQueries()) } }

                failure.message shouldContain "is not a GraphQL literal"
                failure.message shouldContain "limit"
            }
        }
    })
