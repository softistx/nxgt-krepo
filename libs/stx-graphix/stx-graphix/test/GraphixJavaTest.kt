package com.strange.graphix

import graphql.GraphQL
import graphql.schema.StaticDataFetcher
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * The catalog alias is the thing this slice has to get right. A hello-world schema executed
 * through graphql-java 25 proves the coordinate resolved, not just that a jar named GraphQL
 * landed on the classpath.
 */
class GraphixJavaTest :
    FeatureSpec({
        feature("graphql-java") {
            scenario("a hello-world schema executes") {
                val registry = SchemaParser().parse("type Query { hello: String }")
                val wiring =
                    RuntimeWiring
                        .newRuntimeWiring()
                        .type("Query") { it.dataFetcher("hello", StaticDataFetcher("world")) }
                        .build()
                val schema = SchemaGenerator().makeExecutableSchema(registry, wiring)
                val result = GraphQL.newGraphQL(schema).build().execute("{ hello }")

                result.errors shouldBe emptyList()
                val data = result.getData<Map<String, String>>().shouldNotBeNull()
                data["hello"] shouldBe "world"
            }
        }
    })
