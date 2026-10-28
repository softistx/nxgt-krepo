package com.softistx.testing.containers

import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

/** Already on this machine, from the workspace's own cluster — so a run pulls nothing. */
private const val KAFKA_IMAGE = "confluentinc/cp-kafka:latest"

/**
 * Kafka: **one** broker, in KRaft mode.
 *
 * One and not three, and the cost is worth naming rather than hiding. The workspace cluster runs
 * `min.insync.replicas = 2`, so its topics are created with three replicas and `acks = all` there
 * really does wait for a quorum. Against this container a topic can only have one replica, so
 * `acks = all` waits for one broker — the ack path is exercised, the quorum is not. Everything else
 * in the module is unaffected, and `KafkaTestCluster` derives the replication factor from the
 * cluster it actually got rather than asking for three and failing.
 *
 * Three brokers in containers would be faithful and cost roughly 3 GiB and half a minute per run,
 * on a machine whose history includes an OOM killer taking out the IDE. `KAFKA_TEST_BOOTSTRAP` is
 * how a run gets the real thing.
 *
 * The container also ends the `/etc/hosts` requirement the workspace cluster carries: its brokers
 * advertise container hostnames and publish no host ports, so reaching them needs those names
 * resolvable. Testcontainers advertises a mapped port the host can already reach.
 */
fun kafkaContainer(image: String = KAFKA_IMAGE): ContainerService<ConfluentKafkaContainer, String> =
    ContainerService.declare(
        name = "kafka",
        reusing = "KAFKA_TEST_BOOTSTRAP",
        create = { ConfluentKafkaContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("confluentinc/cp-kafka")) },
        endpointOf = ConfluentKafkaContainer::getBootstrapServers,
    )
