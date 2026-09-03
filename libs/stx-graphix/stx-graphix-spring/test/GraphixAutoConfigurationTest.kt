package com.softistx.graphix.spring

import com.softistx.graphix.GraphQLEngineCustomizer
import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.GraphixError
import com.softistx.graphix.error.GraphixErrorScope
import com.softistx.graphix.error.GraphixErrorType.BAD_REQUEST
import com.softistx.graphix.error.GraphixExceptionHandler
import com.softistx.graphix.error.withErrorType
import com.softistx.graphix.error.withMessage
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.get
import com.softistx.graphix.intercept.put
import com.softistx.graphix.scalar.graphQLScalar
import com.softistx.graphix.scalar.scalar
import com.softistx.graphix.schema.Directive
import com.softistx.graphix.schema.GraphixDirective
import com.softistx.graphix.schema.QueryMapping
import com.softistx.graphix.schema.contextParameter
import com.softistx.graphix.spring.fixture.BoomQueries
import com.softistx.graphix.spring.fixture.Caller
import com.softistx.graphix.spring.fixture.ContextQueries
import com.softistx.graphix.spring.fixture.GreetingQueries
import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.language.SourceLocation
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
import java.util.concurrent.CompletableFuture

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
                        sdl shouldContain "scalar Weight"
                    }
            }

            scenario("a directive bean runs, it is not printed in the SDL") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(WiringConfiguration::class.java)
                    .run { context ->
                        // An annotation-derived field directive never reaches the printed schema —
                        // it wraps the fetcher. So the SDL cannot witness the bean; the value can.
                        WebTestClient
                            .bindToRouterFunction(context.getBean("graphixRouter") as RouterFunction<*>)
                            .build()
                            .post()
                            .uri("/graphql")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("""{"query":"{ shout }"}""")
                            .exchange()
                            .expectBody<String>()
                            .value { it shouldContain """"shout":"QUIET"""" }
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

            scenario("GraphixExceptionHandler beans decide what a throw becomes") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(ErrorConfiguration::class.java)
                    .run { context ->
                        WebTestClient
                            .bindToRouterFunction(context.getBean("graphixRouter") as RouterFunction<*>)
                            .build()
                            .post()
                            .uri("/graphql")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("""{"query":"{ boom }"}""")
                            .exchange()
                            .expectBody<String>()
                            .value {
                                it shouldContain "handled by the bean: nope"
                                // The classification reaches a client only through extensions.
                                it shouldContain "BAD_REQUEST"
                            }
                    }
            }

            scenario("GraphQLEngineCustomizer beans reach graphql-java's own builder") {
                runner
                    .withPropertyValues("stx.graphix.enabled=true")
                    .withUserConfiguration(EngineConfiguration::class.java)
                    .run { context ->
                        // The fifth provider, and the only one that customises graphql-java rather
                        // than the Graphix builder — so it is the one a `getBeansOfType` regression
                        // would drop without any schema changing shape.
                        WebTestClient
                            .bindToRouterFunction(context.getBean("graphixRouter") as RouterFunction<*>)
                            .build()
                            .post()
                            .uri("/graphql")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue("""{"query":"{ boom }"}""")
                            .exchange()
                            .expectBody<String>()
                            .value { it shouldContain "handled by the engine customizer bean" }
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
private class EngineConfiguration {
    @Bean
    fun boom() = BoomQueries()

    @Bean
    fun handler(): GraphQLEngineCustomizer =
        object : GraphQLEngineCustomizer {
            override fun GraphQL.Builder.customize() {
                defaultDataFetcherExceptionHandler(TaggedHandler)
            }
        }
}

/**
 * Written out rather than built with `GraphqlErrorBuilder`/`DataFetcherExceptionHandlerResult`'s
 * fluent builders: both are F-bounded (`B extends GraphqlErrorBuilder<B>`), and inferring that
 * type argument here puts the Kotlin compiler into a `StackOverflowError` with no file pointer.
 */
private object TaggedError : GraphQLError {
    override fun getMessage(): String = "handled by the engine customizer bean"

    override fun getLocations(): List<SourceLocation> = emptyList()

    override fun getErrorType(): ErrorClassification = ErrorType.DataFetchingException
}

private object TaggedHandler : DataFetcherExceptionHandler {
    override fun handleException(parameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> =
        CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(TaggedError).build(),
        )
}

@Configuration
private class ErrorConfiguration {
    @Bean
    fun boom() = BoomQueries()

    @Bean
    fun errors(): GraphixExceptionHandler = BeanErrors()
}

/**
 * A bean implementing the interface, so `ObjectProvider<GraphixExceptionHandler>` has something to
 * find. `@Order` decides which of several is asked first, exactly as it does for interceptors.
 */
private class BeanErrors : GraphixExceptionHandler {
    override suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError? =
        if (failure is IllegalStateException) {
            error.withMessage("handled by the bean: ${failure.message}").withErrorType(BAD_REQUEST)
        } else {
            null
        }
}

@Configuration
private class OwnEngineConfiguration {
    @Bean
    fun mine(): Graphix = Graphix { resolvers(GreetingQueries()) }
}

// Not `private`: kotlin-reflect cannot call a member of a private class, so a resolver only ever
// printed into the SDL passes while the same class fails the moment a field is executed.
@GraphQLController
class PriceQueries {
    @QueryMapping
    fun hello(): String = "world"

    /** Carries the directive, so the SDL prints it and executing the field proves it ran. */
    @QueryMapping
    @Directive("uppercase")
    fun shout(): String = "quiet"
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

    // Registers a second scalar rather than doing nothing, so a customizer bean that was collected
    // and one that was silently dropped stop looking the same in the SDL.
    @Bean
    fun extra(): GraphixCustomizer =
        GraphixCustomizer {
            scalar(graphQLScalar("Weight") { serialize { value -> value.toString() } })
        }
}
