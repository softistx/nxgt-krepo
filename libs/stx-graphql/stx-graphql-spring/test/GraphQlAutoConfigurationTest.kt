package com.strange.graphql.spring

import com.strange.graphql.GraphQl
import com.strange.graphql.spring.fixture.GreetingQueries
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class GraphQlAutoConfigurationTest :
    FeatureSpec({
        val runner =
            ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GraphQlAutoConfiguration::class.java))

        feature("opt-in") {
            scenario("nothing is registered until stx.graphql.enabled is true") {
                runner.run { context ->
                    context.getBeansOfType(GraphQl::class.java).isEmpty() shouldBe true
                }
            }

            scenario("enabled with a @GraphQLController bean builds the engine") {
                runner
                    .withPropertyValues("stx.graphql.enabled=true")
                    .withUserConfiguration(GreetingConfiguration::class.java)
                    .run { context ->
                        context.getBeansOfType(GraphQl::class.java).size shouldBe 1
                    }
            }

            scenario("an application's own GraphQl bean wins") {
                runner
                    .withPropertyValues("stx.graphql.enabled=true")
                    .withUserConfiguration(OwnEngineConfiguration::class.java)
                    .run { context ->
                        context.getBeansOfType(GraphQl::class.java).keys shouldBe setOf("mine")
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
    fun mine(): GraphQl = GraphQl { query(GreetingQueries()) }
}
