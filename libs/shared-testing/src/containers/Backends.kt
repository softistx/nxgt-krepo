package com.strange.testing.containers

import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

// The services this repo's libraries test against, each declared once. A backend's particulars —
// which image, what a replica set needs, how its connection string is spelled — belong here rather
// than in every library that talks to it: shared-mongo should say what it needs, not how to
// configure a MongoDBContainer.

/** The image tag the workspace already runs, so a machine that has it pulls nothing. */
private const val MONGO_IMAGE = "mongo:8"

/**
 * MongoDB as a single-node replica set.
 *
 * The replica set is not a preference: `startTransaction` fails outright against a standalone
 * `mongod`, so anything touching a session needs one. `MongoDBContainer` initiates one on startup,
 * which is the reason to use it over a bare [org.testcontainers.containers.GenericContainer].
 *
 * `MONGO_TEST_URI` reuses a server that is already up — the workspace's own replica set, or one CI
 * provisioned.
 */
fun mongoContainer(image: String = MONGO_IMAGE): ContainerService<MongoDBContainer> =
    ContainerService.declare(
        name = "mongodb",
        reusing = "MONGO_TEST_URI",
        create = { MongoDBContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("mongo")) },
        endpointOf = MongoDBContainer::getReplicaSetUrl,
    )
