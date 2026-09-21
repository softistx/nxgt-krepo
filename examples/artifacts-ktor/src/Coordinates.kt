package com.softistx.example.artifacts

import com.softistx.amqp.AmqpConfig
import com.softistx.amqp.ktor.AmqpConnection
import com.softistx.kafka.KafkaConfig
import com.softistx.kafka.ktor.KafkaCluster
import com.softistx.storage.StorageConfig
import com.softistx.storage.ktor.Storage
import io.ktor.server.application.ApplicationPlugin

/**
 * The shape a consumer writes, compiled against the published artifacts.
 *
 * Nothing here connects to anything. The value is in the compilation: a `AmqpConfig(...)` call site
 * only resolves if `io.github.softistx:stx-amqp`'s POM carries the RabbitMQ client that its
 * `configure` parameter is typed against, and the same holds for the other two. A POM that stops
 * naming a dependency, or names it at `provided` where a consumer needs it, fails here and nowhere
 * else in this repository.
 */
object Coordinates {
    val amqp = AmqpConfig(uri = "amqp://broker:5672", connectionName = "artifacts-ktor")

    val kafka = KafkaConfig(bootstrap = "broker:9092", clientId = "artifacts-ktor")

    val storage =
        StorageConfig(
            endpoint = "http://object-store:9000",
            accessKey = "artifacts",
            secretKey = "artifacts",
            region = "eu-west-1",
        )

    /**
     * The three plugins, referenced rather than installed.
     *
     * Installing one opens the resource it names, which is a broker this module does not have. The
     * reference is what has to compile and load; `libs/*/stx-*/test/` is where the plugins are
     * driven against a real service.
     */
    val plugins: List<ApplicationPlugin<*>> = listOf(AmqpConnection, KafkaCluster, Storage)
}
