package com.strange.graphix.spring

import com.strange.graphix.Graphix
import com.strange.graphix.GraphixCustomizer
import com.strange.graphix.scalar.graphQLScalar
import com.strange.graphix.schema.GraphixDirective
import com.strange.graphix.schema.QueryMapping
import com.strange.graphix.spring.fixture.GreetingQueries
import graphql.schema.GraphQLScalarType
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
private class OwnEngineConfiguration {
    @Bean
    fun mine(): Graphix = Graphix { query(GreetingQueries()) }
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
