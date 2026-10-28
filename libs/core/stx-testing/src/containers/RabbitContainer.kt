package com.softistx.testing.containers

import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

/** What the workspace runs, and already on this machine — so a run pulls nothing. */
private const val RABBITMQ_IMAGE = "rabbitmq:4-management"

/**
 * RabbitMQ, with the credentials it generates rather than ones somebody had to export.
 *
 * This is the case where a container settles an argument. `AMQP_TEST_URI` has no default and never
 * will — a URI carries its credentials, and a credential with a default is a credential in source
 * control — so before this the specs simply skipped on any machine where nobody had exported one.
 * A container has its own credentials to hand out, so there is nothing to default and no reason to
 * skip.
 *
 * The URI is built rather than taken from `getAmqpUrl()`, which carries no credentials, and the
 * vhost is left **off the path entirely**: the default vhost is `/`, and a URI ending in a bare `/`
 * asks for the *empty* vhost, which the broker refuses with a message about permissions that says
 * nothing about the cause. Spelling it out means `%2F`, which is what the override below has to do.
 */
fun rabbitContainer(image: String = RABBITMQ_IMAGE): ContainerService<RabbitMQContainer, String> =
    ContainerService.declare(
        name = "rabbitmq",
        reusing = "AMQP_TEST_URI",
        create = { RabbitMQContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("rabbitmq")) },
        endpointOf = { "amqp://${it.adminUsername}:${it.adminPassword}@${it.host}:${it.amqpPort}" },
    )
