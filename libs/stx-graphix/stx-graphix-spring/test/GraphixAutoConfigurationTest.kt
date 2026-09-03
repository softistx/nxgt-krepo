package com.softistx.graphix.spring

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.get
import com.softistx.graphix.intercept.put
import com.softistx.graphix.scalar.graphQLScalar
import com.softistx.graphix.schema.GraphixDirective
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.contextParameter
import com.softistx.graphix.spring.fixture.Caller
import com.softistx.graphix.spring.fixture.ContextQueries
import com.softistx.graphix.spring.fixture.GreetingQueries
import graphql.schema.GraphQLScalarType
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.server.RouterFunction

class GraphixAutoConfigurationTest :
    FeatureSpec({
        val runner =
            ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GraphixAutoConfiguration::class.java))

        feature("opt-in") {
            scenario("nothing is registered until stx.graphix.enabled is true") {
                runner.run { context ->
                    context.getBeansOfType(Graphix::class.java).isEmpty() shouldBe true
                }
            }

            scenario("enabled with a @GraphQLController bean builds the engine") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(GreetingConfiguration::class.java)
                    .run { context ->
                        context.getBeansOfType(Graphix::class.java).size shouldBe 1
                    }
            }

            scenario("graphql-ws registers a WebSocket handler") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true", "stx.graphix.subscriptions=graphql-ws")
                    .withUserConfiguration(GreetingConfiguration::class.java)
                    .run { context ->
                        context.getBeansOfType(GraphixWebSocketHandler::class.java).size shouldBe 1
                    }
            }

            scenario("sse does not register a WebSocket handler") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(GreetingConfiguration::class.java)
                    .run { context ->
                        context.getBeansOfType(GraphixWebSocketHandler::class.java).isEmpty() shouldBe true
                    }
            }

            scenario("scalar, directive and customizer beans are applied") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(WiringConfiguration::class.java)
                    .run { context ->
                        context.getBeansOfType(Graphix::class.java).size shouldBe 1
                        val sdl = context.getBean(Graphix::class.java).sdl()
                        sdl shouldContain "scalar Money"
                    }
            }

            scenario("GraphixInterceptor beans are applied, outermost in @Order") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(InterceptorConfiguration::class.java)
                    .run { context ->
                        // The second interceptor reads what the first put there, so "ab" is only
                        // reachable in that order — the other way round it would find nothing.
                        WebTestClient
                            .bindToRouterFunction(context.getBean("graphixRouter") as RouterFunction<*>)
                            .build()
                            .post()
                            .uri("/graphql")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("""{"query":"{ who }"}""")
                            .exchange()
                            .expectBody<String>()
                            .value { it shouldContain """"who":"ab"""" }
                    }
            }

            scenario("an application's own Graphix bean wins") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(OwnEngineConfiguration::class.java)
                    .run { context ->
                        context.getBeansOfType(Graphix::class.java).keys shouldBe setOf("mine")
                    }
            }
        }

        feature("the sandbox") {
            scenario("no router until stx.graphix.sandbox is true") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(WiringConfiguration::class.java)
                    .run { context ->
                        context.containsBean("graphixSandboxRouter") shouldBe false
                    }
            }

            scenario("stx.graphix.sandbox=true adds a second RouterFunction beside the GraphQL one") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true", "stx.graphix.sandbox=true")
                    .withUserConfiguration(WiringConfiguration::class.java)
                    .run { context ->
                        context.containsBean("graphixSandboxRouter") shouldBe true
                        context.getBeansOfType(RouterFunction::class.java).size shouldBe 2
                    }
            }
        }
    })

@Configuration
private class GreetingConfiguration {
    @Bean
    fun greetings() = GreetingQueries()
}

@Configuration
private class InterceptorConfiguration {
    @Bean
    fun who() = ContextQueries()

    // The application's own context type, registered the way an application would: a customizer
    // bean, since the engine here is the auto-configured one.
    @Bean
    fun callerContext(): GraphixCustomizer = GraphixCustomizer { contextParameter(Caller::class) }

    @Bean
    @Order(1)
    fun first(): GraphixInterceptor =
        GraphixInterceptor {
            put(Caller("a"))
            proceed()
        }

    @Bean
    @Order(2)
    fun second(): GraphixInterceptor =
        GraphixInterceptor {
            put(Caller(get<Caller>()!!.name + "b"))
            proceed()
        }
}

@Configuration
private class OwnEngineConfiguration {
    @Bean
    fun mine(): Graphix = Graphix { resolvers(GreetingQueries()) }
}

@GraphQLController
private class PriceQueries {
    @QueryMapping
    fun hello(): String = "world"
}

@Configuration
private class WiringConfiguration {
    @Bean
    fun greetings() = PriceQueries()

    @Bean
    fun money(): GraphQLScalarType = graphQLScalar("Money") { serialize { value -> value.toString() } }

    @Bean
    fun uppercase(): GraphixDirective =
        GraphixDirective("uppercase") {
            val value = proceed()
            (value as? String)?.uppercase() ?: value
        }

    @Bean
    fun extra(): GraphixCustomizer = GraphixCustomizer { }
}
