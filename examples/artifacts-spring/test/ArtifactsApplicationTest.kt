package com.softistx.example.artifacts.spring

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * The context starts with all three auto-configurations on the classpath and none of them on.
 *
 * A failure here is a published artifact being wrong, not this application: a missing
 * `AutoConfiguration.imports`, a class the entry names that is not in the jar, or a dependency the
 * POM stopped carrying that the auto-configuration's own signatures reach for. None of those shows
 * up anywhere else in this repository, because nothing else consumes these nine coordinates.
 */
@SpringBootTest
class ArtifactsApplicationTest(
    private val context: ApplicationContext,
) : StringSpec({
        "the context starts with the three auto-configurations present" {
            context shouldNotBe null
        }

        "and none of them has built anything, because none was turned on" {
            // `@ConditionalOnProperty(stx.<name>.enabled)` with the property unset. Naming the classes
            // by string rather than by type keeps this a classpath question: the bean is absent because
            // the condition said so, not because the jar is missing — which the startup above settled.
            for (name in listOf("com.softistx.amqp.Amqp", "com.softistx.kafka.Kafka", "com.softistx.storage.ObjectStorage")) {
                val type = Class.forName(name)
                context.getBeanNamesForType(type).size shouldBe 0
            }
        }
    })
