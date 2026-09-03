package com.softistx.graphix

import com.softistx.graphix.fixture.ShelfQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * What graphix reads off an object, and when.
 *
 * The claim these pin is load-bearing somewhere else entirely: a JPA entity's lazy association
 * *throws* when read, so a `@BatchMapping` keys on the foreign key column beside it. That only works
 * if the association is never read — and "never read" is a promise about this engine, not about JPA.
 *
 * Two separate guarantees, and they are not the same strength. A property is read only when its
 * field is selected; a property outside the `@Serializable` descriptor has no field to select.
 * `docs/jpa-mapping.md` is where the mapping side of this lives.
 */
class UnselectedFieldTest :
    FeatureSpec({
        feature("a property is read when its field is selected") {
            scenario("and at no other time, even when it is a field of the schema") {
                // `contents` is in the schema here — nothing excluded it. Selecting `title` still
                // does not read it, which is the guarantee a DataLoader on a foreign key rests on.
                val graphql = Graphix { resolvers(ShelfQueries()) }

                graphql.sdl() shouldContain "contents: String!"
                val result = graphql.execute(GraphixRequest("{ shelf { title } }"))

                result.isOk shouldBe true
                result.data?.get("shelf") shouldBe mapOf("title" to "Fiction")
            }

            scenario("so selecting it is what surfaces the throw, as a field error") {
                // The other half of the same fact, and the reason @GraphQLIgnore below is advice
                // rather than decoration: a lazy association left in the descriptor is selectable,
                // and a client that selects it gets the failure the batch mapping was avoiding.
                val error =
                    Graphix { resolvers(ShelfQueries()) }
                        .execute(GraphixRequest("{ shelf { contents } }"))
                        .errors
                        .single()

                error.message shouldContain "read a property the client did not select"
                error.path shouldBe listOf("shelf", "contents")
            }
        }

        feature("a property outside the descriptor has no field at all") {
            scenario("so @GraphQLIgnore makes it unselectable rather than merely unselected") {
                // Spelled as the whole type rather than as an absent substring: `Shelf` declares a
                // `contents` two lines up, so anything narrower passes for the wrong reason.
                Graphix { resolvers(ShelfQueries()) }
                    .sdl() shouldContain "type GuardedShelf {\n  title: String!\n}"
            }

            scenario("and asking for it is a validation error, before any resolver runs") {
                val error =
                    Graphix { resolvers(ShelfQueries()) }
                        .execute(GraphixRequest("{ guarded { contents } }"))
                        .errors
                        .single()

                error.message shouldContain "contents"
            }
        }
    })
