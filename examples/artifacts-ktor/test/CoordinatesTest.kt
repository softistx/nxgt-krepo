package com.softistx.example.artifacts

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * The runtime half of the check the compile started.
 *
 * Compiling proves the POMs name what the signatures reach for; loading these classes proves the
 * jars are actually resolvable and that nothing they touch on the way in is missing. Both halves
 * are cheap, neither needs a broker, and together they are the only thing in this repository that
 * looks at these nine artifacts as a consumer sees them.
 */
class CoordinatesTest :
    StringSpec({
        "each library's configuration is constructible from the published artifact" {
            Coordinates.amqp.uri shouldBe "amqp://broker:5672"
            Coordinates.kafka.bootstrap shouldBe "broker:9092"
            Coordinates.storage.endpoint shouldBe "http://object-store:9000"
        }

        "a default left untouched is the library's own, not one restated here" {
            // If a default moves, this is where it is noticed — which is also a reminder that these
            // values are part of the published API.
            AmqpDefaults.heartbeat shouldBe Coordinates.amqp.heartbeat
        }

        "the three Ktor plugins load, each under its own key" {
            // Not the plugin's class — `createApplicationPlugin` gives all three the same Ktor
            // implementation type, which says nothing. The attribute key is what Ktor installs under,
            // and it is the name each library chose.
            Coordinates.plugins shouldHaveSize 3
            Coordinates.plugins.map { it.key.name } shouldBe listOf("Amqp", "Kafka", "Storage")
        }
    })

private object AmqpDefaults {
    val heartbeat =
        com.softistx.amqp
            .AmqpConfig(uri = "amqp://unused")
            .heartbeat
}
