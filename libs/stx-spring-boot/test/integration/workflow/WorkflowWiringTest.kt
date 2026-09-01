package com.strange.spring.integration.workflow

import com.strange.redis.Redis
import com.strange.redis.RedisConfig
import com.strange.testing.containers.redisContainer
import com.strange.workflow.Workflow
import com.strange.workflow.WorkflowEngine
import com.strange.workflow.WorkflowStatus
import com.strange.workflow.dsl.step
import com.strange.workflow.store.InMemoryStore
import com.strange.workflow.store.WorkflowStore
import com.strange.workflow.workflow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Serializable
private data class Order(
    val paid: Boolean = false,
)

private val PAY =
    workflow<Order>("pay") {
        step("charge") { context.copy(paid = true) }
    }

private val REFUND =
    workflow<Order>("refund") {
        step("credit") { context.copy(paid = false) }
    }

/**
 * What `stx.workflow` wires, and what it leaves to the application.
 *
 * The load-bearing one is registration. An instance is stored under its workflow's *name*, and an
 * engine that cannot look that name up cannot resume it after a restart — so "every `Workflow` bean
 * is registered" is not a convenience, it is the difference between a fleet that recovers and one
 * that fails on half its instances. Asserted by running one, because `start` refuses a workflow the
 * engine does not know.
 */
class WorkflowWiringTest :
    StringSpec({

        "nothing is built until an application asks" {
            runner().withUserConfiguration(StoreConfig::class.java).run { context ->
                context.getBeanNamesForType(WorkflowEngine::class.java).size shouldBe 0
            }
        }

        "enabled, with a store, gives an engine" {
            runner()
                .withPropertyValues("stx.workflow.enabled=true")
                .withUserConfiguration(StoreConfig::class.java)
                .run { context ->
                    context.getBeanNamesForType(WorkflowEngine::class.java).size shouldBe 1
                }
        }

        "enabled without a store builds nothing, and does not fail the context" {
            // A store is a connection somebody opened. An application that turned the key on before
            // declaring one should hear about it when it asks for the engine, not as a refresh
            // failure listing a bean it has never seen.
            runner()
                .withPropertyValues("stx.workflow.enabled=true")
                .run { context ->
                    context.startupFailure shouldBe null
                    context.getBeanNamesForType(WorkflowEngine::class.java).size shouldBe 0
                }
        }

        "every Workflow bean is registered with it" {
            runner()
                .withPropertyValues("stx.workflow.enabled=true")
                .withUserConfiguration(StoreConfig::class.java, WorkflowsConfig::class.java)
                .run { context ->
                    val engine = context.getBean(WorkflowEngine::class.java)
                    runBlocking {
                        engine.start(PAY, Order()).status shouldBe WorkflowStatus.Completed
                        engine.start(REFUND, Order()).status shouldBe WorkflowStatus.Completed
                    }
                }
        }

        "an application's own engine wins" {
            runner()
                .withPropertyValues("stx.workflow.enabled=true")
                .withUserConfiguration(StoreConfig::class.java, OwnEngineConfig::class.java)
                .run { context ->
                    context.getBeanNamesForType(WorkflowEngine::class.java).toList() shouldBe listOf("ownEngine")
                }
        }

        "the worker is off unless it is asked for" {
            runner()
                .withPropertyValues("stx.workflow.enabled=true")
                .withUserConfiguration(StoreConfig::class.java)
                .run { context ->
                    context.getBeanNamesForType(WorkflowWorkerLifecycle::class.java).size shouldBe 0
                }
        }

        "asked for, it is running by the time the context is" {
            runner()
                .withPropertyValues("stx.workflow.enabled=true", "stx.workflow.worker.enabled=true")
                .withUserConfiguration(StoreConfig::class.java)
                .run { context ->
                    // A SmartLifecycle rather than an @PostConstruct: started after the context is
                    // refreshed, so the instances it resumes meet finished collaborators.
                    context.getBean(WorkflowWorkerLifecycle::class.java).isRunning shouldBe true
                }
        }

        "a Redis bean becomes the store, over the connection it already has".config(enabled = redisServer.available) {
            runner()
                .withPropertyValues("stx.workflow.enabled=true")
                .withUserConfiguration(OwnRedisConfig::class.java)
                .run { context ->
                    context.getBean(WorkflowStore::class.java)::class.simpleName shouldBe "RedisWorkflowStore"
                    context.getBeanNamesForType(WorkflowEngine::class.java).size shouldBe 1
                }
        }
    })

private fun runner() =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WorkflowIntegrationAutoConfiguration::class.java))

// Named `…Config` rather than after the bean they declare: a configuration class registers under
// its own uncapitalised name, so a class `Store` holding `@Bean fun store()` is two definitions
// claiming the name `store`, and the context refuses to start.
@Configuration(proxyBeanMethods = false)
private class StoreConfig {
    @Bean fun store(): WorkflowStore = InMemoryStore()
}

@Configuration(proxyBeanMethods = false)
private class WorkflowsConfig {
    @Bean fun pay(): Workflow<*> = PAY

    @Bean fun refund(): Workflow<*> = REFUND
}

@Configuration(proxyBeanMethods = false)
private class OwnEngineConfig {
    @Bean fun ownEngine(store: WorkflowStore): WorkflowEngine = WorkflowEngine(store)
}

/**
 * A real server, because `Redis.connect` really connects.
 *
 * That is what the first draft of this spec got wrong — it pointed at a closed port on the theory
 * that Lettuce is lazy, and the context failed to refresh with `Unable to connect`. Worth knowing
 * about the connection plugins generally: a wrong host is a startup failure, not a first-call one.
 */
@Configuration(proxyBeanMethods = false)
private class OwnRedisConfig {
    @Bean fun redis(): Redis = Redis.connect(RedisConfig(uri = redisServer.requireEndpoint(), namespace = "stx-workflow-wiring"))
}

/** The workspace's Redis when it is up, a container for the run otherwise — see `stx-testing`. */
private val redisServer = redisContainer()
