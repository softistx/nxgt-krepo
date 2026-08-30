package com.strange.graphix

import com.strange.graphix.fixture.BadQueries
import com.strange.graphix.fixture.GreetingQueries
import com.strange.graphix.fixture.ProductQueries
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class SchemaTest :
    FeatureSpec({
        feature("schema from annotations") {
            scenario("query functions become fields, and @Serializable types become objects") {
                val sdl = Graphix { query(ProductQueries()) }.sdl()
                sdl shouldContain "type Query"
                sdl shouldContain "product(id: String!): Product"
                sdl shouldContain "type Product"
                sdl shouldContain "name: String!"
                sdl shouldContain "tags: [String!]!"
            }

            scenario("a @GraphQLIgnore property is not a GraphQL field") {
                val sdl = Graphix { query(ProductQueries()) }.sdl()
                sdl shouldNotContain "secret"
            }

            scenario("@GraphQLName renames a field") {
                val sdl = Graphix { query(GreetingQueries()) }.sdl()
                sdl shouldContain "shout"
                sdl shouldNotContain "loud"
            }

            scenario("enums and lists round-trip through SerialDescriptor") {
                val sdl = Graphix { query(ProductQueries()) }.sdl()
                sdl shouldContain "enum Size"
                sdl shouldContain "sizes: [Size!]!"
            }

            scenario("a type that is not @Serializable fails naming that type") {
                val failure = shouldThrow<GraphixException> { Graphix { query(BadQueries()) } }
                failure.message shouldContain "NotSerializable"
                failure.message shouldContain "not @Serializable"
            }

            scenario("no query root is a schema-build failure") {
                shouldThrow<GraphixException> { Graphix { } }
            }
        }
    })
