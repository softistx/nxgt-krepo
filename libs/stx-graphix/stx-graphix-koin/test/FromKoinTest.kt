package com.softistx.graphix.koin

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.koin.fixture.CallerQueries
import com.softistx.graphix.koin.fixture.EchoQueries
import com.softistx.graphix.koin.fixture.GreetingQueries
import com.softistx.graphix.koin.fixture.ShoutQueries
import com.softistx.graphix.koin.fixture.UnmarkedQueries
import com.softistx.graphix.koin.fixture.callerInterceptor
import com.softistx.graphix.koin.fixture.moneyCustomizer
import com.softistx.graphix.koin.fixture.uppercaseDirective
import com.softistx.graphix.scalar.graphQLScalar
import com.softistx.graphix.schema.GraphixDirective
import graphql.schema.GraphQLScalarType
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * What `fromKoin()` takes out of a container, and what it leaves behind.
 *
 * The specs build the container with Koin's own DSL rather than its annotations: the DSL needs no
 * compiler plugin, and what is being tested is the *collection*, which is `getAll<T>()` either way.
 */
class FromKoinTest :
    FeatureSpec({

        feature("resolvers") {
            scenario("every GraphixResolver single becomes part of the schema") {
                val koin =
                    koinApplication {
                        modules(
                            module {
                                single { GreetingQueries() } bind GraphixResolver::class
                                single { EchoQueries() } bind GraphixResolver::class
                            },
                        )
                    }.koin
                val engine = Graphix { fromKoin(koin) }

                engine.execute(GraphixRequest("{ hello }")).data?.get("hello") shouldBe "world"
                engine.execute(GraphixRequest("""{ echo(text: "hi") }""")).data?.get("echo") shouldBe "hi"
            }

            scenario("a single that does not implement it is left out") {
                val koin =
                    koinApplication {
                        modules(
                            module {
                                single { GreetingQueries() } bind GraphixResolver::class
                                single { UnmarkedQueries() }
                            },
                        )
                    }.koin

                Graphix { fromKoin(koin) }.sdl() shouldNotContain "secret"
            }

            scenario("an empty container builds nothing rather than failing") {
                val koin = koinApplication { modules(module { }) }.koin

                Graphix {
                    fromKoin(koin)
                    resolvers(GreetingQueries())
                }.execute(GraphixRequest("{ hello }")).data?.get("hello") shouldBe "world"
            }
        }

        feature("the rest needs no marker, because it is already a type") {
            scenario("scalars, directives and customizers come across") {
                val koin =
                    koinApplication {
                        modules(
                            module {
                                single { ShoutQueries() } bind GraphixResolver::class
                                single<GraphQLScalarType> { weightScalar() }
                                single<GraphixDirective> { uppercaseDirective() }
                                single<GraphixCustomizer> { moneyCustomizer() }
                            },
                        )
                    }.koin
                val engine = Graphix { fromKoin(koin) }

                // The scalar bean, and the one a customizer bean registered.
                engine.sdl() shouldContain "scalar Weight"
                engine.sdl() shouldContain "scalar Money"
                // A field directive shows in the SDL only where a field uses it, so the proof that
                // it was collected is that it ran.
                engine.execute(GraphixRequest("{ shout }")).data?.get("shout") shouldBe "QUIET"
            }

            scenario("interceptors come across and run") {
                val koin =
                    koinApplication {
                        modules(
                            module {
                                single { CallerQueries() } bind GraphixResolver::class
                                single<GraphixInterceptor> { callerInterceptor("ada") }
                            },
                        )
                    }.koin

                Graphix { fromKoin(koin) }
                    .execute(GraphixRequest("{ who }"))
                    .data
                    ?.get("who") shouldBe "ada"
            }
        }
    })

private fun weightScalar(): GraphQLScalarType = graphQLScalar("Weight") { serialize { value -> value.toString() } }
