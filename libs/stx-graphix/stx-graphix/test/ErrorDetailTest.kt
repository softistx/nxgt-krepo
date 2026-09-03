package com.softistx.graphix

import com.softistx.graphix.fixture.BoomQueries
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.http.GraphixHttpRequest
import com.softistx.graphix.http.toGraphixRequest
import com.softistx.graphix.http.toHttp
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A GraphQL error is more than a message and a path: `locations`, `extensions` and a
 * classification are what a client library reads to tell a validation failure from a resolver
 * throw. All three used to be dropped on the floor.
 */
class ErrorDetailTest :
    FeatureSpec({
        feature("a GraphQL error") {
            scenario("a resolver throw carries its location and its classification") {
                val graphql = Graphix { resolvers(BoomQueries()) }
                val result = graphql.execute(GraphixRequest("{ boom }"))

                result.isOk shouldBe false
                val error = result.errors.first()
                error.errorType shouldBe "DataFetchingException"
                error.locations.first().line shouldBe 1
                error.locations.first().column shouldBe 3
            }

            scenario("a validation error is classified as one") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val result = graphql.execute(GraphixRequest("{ nope }"))

                result.isOk shouldBe false
                result.errors.first().errorType shouldBe "ValidationError"
                result.errors
                    .first()
                    .locations
                    .shouldNotBeNull()
            }

            scenario("locations reach the HTTP envelope") {
                val graphql = Graphix { resolvers(BoomQueries()) }
                val http = graphql.execute(GraphixRequest("{ boom }")).toHttp()

                val error = http.errors.shouldNotBeNull().first()
                error.locations
                    .shouldNotBeNull()
                    .first()
                    .line shouldBe 1
            }

            scenario("an error with no extensions omits them rather than sending an empty object") {
                val graphql = Graphix { resolvers(BoomQueries()) }
                val http = graphql.execute(GraphixRequest("{ boom }")).toHttp()

                http.errors
                    .shouldNotBeNull()
                    .first()
                    .extensions shouldBe null
                http.extensions shouldBe null
            }
        }

        feature("request extensions") {
            scenario("the HTTP envelope carries them into the operation") {
                val body = GraphixHttpRequest(query = "{ hello }", extensions = JsonObject(mapOf("trace" to JsonPrimitive("t1"))))

                body.toGraphixRequest().extensions shouldBe mapOf("trace" to "t1")
            }

            scenario("an operation runs unchanged when they are present") {
                val graphql = Graphix { resolvers(GreetingQueries()) }
                val result =
                    graphql.execute(GraphixRequest("{ hello }", extensions = mapOf("trace" to "t1")))

                result.isOk shouldBe true
                result.data shouldBe mapOf("hello" to "world")
            }

            scenario("a request without them is not the same object as one with them") {
                GraphixRequest("{ hello }") shouldNotBe GraphixRequest("{ hello }", extensions = mapOf("a" to 1))
            }
        }
    })
