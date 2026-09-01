package com.softistx.graphix

import com.softistx.graphix.fixture.BadSubscriptions
import com.softistx.graphix.fixture.Caller
import com.softistx.graphix.fixture.ContextSubscriptions
import com.softistx.graphix.fixture.GreetingQueries
import com.softistx.graphix.fixture.TickPublisherSubscriptions
import com.softistx.graphix.fixture.TickSubscriptions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

class SubscriptionTest :
    FeatureSpec({
        feature("schema") {
            scenario("Flow<T> unwraps to T on the Subscription root") {
                val sdl =
                    Graphix {
                        query(GreetingQueries())
                        subscription(TickSubscriptions())
                    }.sdl()
                sdl shouldContain "type Subscription"
                sdl shouldContain "ticks: Int!"
            }

            scenario("a @SubscriptionMapping that does not return Flow or Publisher fails schema build") {
                val failure =
                    shouldThrow<GraphixException> {
                        Graphix {
                            query(GreetingQueries())
                            subscription(BadSubscriptions())
                        }
                    }
                failure.message shouldContain "Flow<T> or Publisher<T>"
            }
        }

        feature("subscribe") {
            scenario("a Flow of scalars becomes one GraphixResult per event") {
                val graphql =
                    Graphix {
                        query(GreetingQueries())
                        subscription(TickSubscriptions())
                    }
                val values =
                    graphql
                        .subscribe(GraphixRequest("subscription { ticks }"))
                        .map { it.data?.get("ticks") }
                        .toList()
                values shouldBe listOf(1, 2, 3)
            }

            scenario("a reactive-streams Publisher is accepted the same way") {
                val graphql =
                    Graphix {
                        query(GreetingQueries())
                        subscription(TickPublisherSubscriptions())
                    }
                val values =
                    graphql
                        .subscribe(GraphixRequest("subscription { ticks }"))
                        .map { it.data?.get("ticks") }
                        .toList()
                values shouldBe listOf(1, 2)
            }

            scenario("@GraphQLContext is taken from subscribe's context map") {
                val graphql =
                    Graphix {
                        query(GreetingQueries())
                        subscription(ContextSubscriptions())
                    }
                val values =
                    graphql
                        .subscribe(
                            GraphixRequest("subscription { who }"),
                            context = mapOf(Caller::class to Caller("fr")),
                        ).map { it.data?.get("who") }
                        .toList()
                values shouldBe listOf("fr")
            }

            scenario("execute on a subscription throws rather than serialising the Publisher") {
                val graphql =
                    Graphix {
                        query(GreetingQueries())
                        subscription(TickSubscriptions())
                    }
                val failure =
                    shouldThrow<GraphixException> {
                        graphql.execute(GraphixRequest("subscription { ticks }"))
                    }
                failure.message shouldContain "subscribe"
            }

            scenario("subscribe on a query throws rather than emitting one item") {
                val graphql = Graphix { query(GreetingQueries()) }
                val failure =
                    shouldThrow<GraphixException> {
                        graphql.subscribe(GraphixRequest("{ hello }")).toList()
                    }
                failure.message shouldContain "execute"
            }
        }
    })
