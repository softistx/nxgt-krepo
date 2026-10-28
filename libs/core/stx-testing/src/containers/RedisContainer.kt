package com.softistx.testing.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/** Small and quick to start; the specs use only core commands, so Redis Stack buys nothing here. */
private const val REDIS_IMAGE = "redis:8-alpine"

/**
 * Redis, on database 15.
 *
 * There is no published Testcontainers module for Redis, so this is a [GenericContainer] waiting on
 * the port — the shape `ContainerServiceTest` already proves with nginx.
 *
 * The `/15` is kept from when these specs shared the workspace's server, where writing to db 0 would
 * have landed next to another application's keys. It costs nothing against a container and means the
 * specs read identically whichever one they got.
 *
 * `REDIS_TEST_URI` reuses a server that is already up.
 */
fun redisContainer(image: String = REDIS_IMAGE): ContainerService<GenericContainer<*>, String> =
    ContainerService.declare(
        name = "redis",
        reusing = "REDIS_TEST_URI",
        create = {
            GenericContainer(DockerImageName.parse(image))
                .withExposedPorts(REDIS_PORT)
                .waitingFor(Wait.forListeningPort())
        },
        endpointOf = { "redis://${it.host}:${it.getMappedPort(REDIS_PORT)}/15" },
    )

private const val REDIS_PORT = 6379
