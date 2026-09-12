package com.softistx.graphix

import com.softistx.graphix.fixture.AuditQueries
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.ProductQueries
import com.softistx.graphix.schema.fieldDirective
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** A directive on an OBJECT or an INTERFACE, and the introspection switch. */
class DirectiveLocationTest :
    FeatureSpec({
        fun audit(block: GraphixBuilder.() -> Unit) =
            Graphix {
                schemaLocations("classpath:graphix-audit/")
                fieldDirective("shout") { (proceed() as? String)?.uppercase() ?: proceed() }
                block()
            }

        feature("directive locations") {
            scenario("a directive on an object wraps every one of its fields") {
                val graphql = audit { resolvers(AuditQueries()) }
                val result = graphql.execute(GraphixRequest("{ ticket { title owner } }"))

                result.isOk shouldBe true
                result.data?.get("ticket") shouldBe mapOf("title" to "DUNE", "owner" to "FRANK")
            }

            scenario("a type without the directive is untouched") {
                val graphql = audit { resolvers(AuditQueries()) }
                val result = graphql.execute(GraphixRequest("{ plain { title } }"))

                result.data?.get("plain") shouldBe mapOf("title" to "dune")
            }

            scenario("the same directive on a single field still works") {
                val graphql = audit { resolvers(AuditQueries()) }

                graphql.execute(GraphixRequest("{ loud }")).data shouldBe mapOf("loud" to "QUIET")
            }
        }

        feature("introspection") {
            scenario("__schema answers by default") {
                val graphql = Graphix { resolvers(GreetingQueries()) }

                graphql.execute(GraphixRequest("{ __schema { queryType { name } } }")).isOk shouldBe true
            }

            scenario("turned off, __schema is one error and no data") {
                val graphql =
                    Graphix {
                        introspection(false)
                        resolvers(GreetingQueries())
                    }
                val result = graphql.execute(GraphixRequest("{ __schema { queryType { name } } }"))

                result.isOk shouldBe false
                result.errors.first().message shouldContain "ntrospection"
                result.data shouldBe null
            }

            scenario("turned off, __type is refused too") {
                val graphql =
                    Graphix {
                        introspection(false)
                        resolvers(ProductQueries())
                    }

                graphql.execute(GraphixRequest("""{ __type(name: "Product") { name } }""")).isOk shouldBe false
            }

            scenario("turned off, an ordinary query is unaffected") {
                val graphql =
                    Graphix {
                        introspection(false)
                        resolvers(GreetingQueries())
                    }

                graphql.execute(GraphixRequest("{ hello }")).data shouldBe mapOf("hello" to "world")
            }

            scenario("__typename is not introspection and keeps working") {
                val graphql =
                    Graphix {
                        introspection(false)
                        resolvers(ProductQueries())
                    }
                val result = graphql.execute(GraphixRequest("""{ product(id: "p1") { __typename } }"""))

                result.isOk shouldBe true
                (result.data?.get("product") as Map<*, *>)["__typename"] shouldBe "Product"
            }
        }
    })
