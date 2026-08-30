package com.strange.graphix

import com.strange.graphix.fixture.BookFields
import com.strange.graphix.fixture.BookQueries
import com.strange.graphix.fixture.DelayedBookFields
import com.strange.graphix.fixture.DuplicateNamedLoaders
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class DeclaredLoaderTest :
    FeatureSpec({
        feature("schema") {
            scenario("dataLoader SchemaMapping fields appear on the parent type") {
                val sdl =
                    Graphix {
                        query(BookQueries())
                        type(BookFields())
                    }.sdl()
                sdl shouldContain "type Book"
                sdl shouldContain "author: Author"
                sdl shouldContain "snippets(limit: Int): [String!]!"
            }

            scenario("duplicate DataLoader names fail schema build") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(BookQueries())
                            type(DuplicateNamedLoaders())
                        }
                    }
                failure.message shouldContain "duplicate DataLoader 'authorsById'"
            }
        }

        feature("dataLoader on a mapping class") {
            scenario("SchemaMapping load() batches by key") {
                val fields = BookFields()
                val graphql =
                    Graphix {
                        query(BookQueries())
                        type(fields)
                    }
                val one = graphql.execute(GraphixRequest("{ book { title author { name } } }"))
                one.errors.shouldBeEmpty()
                ((one.data.shouldNotBeNull()["book"] as Map<*, *>)["author"] as Map<*, *>)["name"] shouldBe "Frank"
                fields.authorLoads.get() shouldBe 1
            }

            scenario("two parents share one batch") {
                val fields = BookFields()
                val graphql =
                    Graphix {
                        query(BookQueries())
                        type(fields)
                    }
                val result = graphql.execute(GraphixRequest("{ books { title author { name } } }"))
                result.errors.shouldBeEmpty()
                fields.authorLoads.get() shouldBe 1
                val listed = result.data.shouldNotBeNull()["books"] as List<*>
                listed.size shouldBe 2
                ((listed[0] as Map<*, *>)["author"] as Map<*, *>)["name"] shouldBe "Frank"
                ((listed[1] as Map<*, *>)["author"] as Map<*, *>)["name"] shouldBe "Frank"
            }

            scenario("field arguments are part of the loader key") {
                val fields = BookFields()
                val graphql =
                    Graphix {
                        query(BookQueries())
                        type(fields)
                    }
                val result = graphql.execute(GraphixRequest("{ books { snippets(limit: 3) } }"))
                result.errors.shouldBeEmpty()
                fields.reviewLoads.get() shouldBe 1
                val first = (result.data.shouldNotBeNull()["books"] as List<*>)[0] as Map<*, *>
                (first["snippets"] as List<*>).size shouldBe 3
            }

            scenario("load() after other suspend work still completes") {
                val fields = DelayedBookFields()
                val graphql =
                    Graphix {
                        query(BookQueries())
                        type(fields)
                    }
                val result = graphql.execute(GraphixRequest("{ books { author { name } } }"))
                result.errors.shouldBeEmpty()
                fields.authorLoads.get() shouldBeGreaterThanOrEqual 1
                ((result.data.shouldNotBeNull()["books"] as List<*>)[0] as Map<*, *>)["author"]
                    .let { (it as Map<*, *>)["name"] shouldBe "Frank" }
            }
        }
    })
