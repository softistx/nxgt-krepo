# shared-testing

Test-only support the libraries here share. Today: the backing services their integration specs
talk to, declared rather than assumed.

```kotlin
private val mongo = mongoContainer()

feature("reading").config(enabled = mongo.available) { … }
```

## Where a service comes from is resolved, not assumed

In order:

1. **The environment variable the service was declared with.** `MONGO_TEST_URI` names a server that
   is already up — the workspace's own, or one CI provisioned. Nothing is started.
2. **A container**, started once for the run.
3. **Neither** — `available` is `false`, and every spec gated on it is *skipped*. A machine without
   Docker reports skipped tests, not a red build over something that is not the code.

The container is the **default**, and that is the change worth naming. A suite that only passes on a
machine with the right daemons already running passes for the wrong reason, and tells someone who
checks the repo out tomorrow nothing at all. The override stays because a developer with the
workspace already up should not pay a container start per run.

## Started once per JVM, cleaned up twice over

A container start is seconds; twenty specs each restarting one would spend minutes doing it. So the
start is lazy and shared, and the isolation a spec needs is a database or a namespace of its own
*inside* it — cheap, and the same discipline the specs already follow against the workspace's own
servers.

Teardown has a belt and braces. A shutdown hook stops every started container when the JVM exits,
and Testcontainers' Ryuk sidecar removes what the run created even when the JVM is killed and no
hook gets to run. `docker ps -a` after a run should show nothing new; if it does, that is a bug here.

## Declaring a backend

A backend's particulars — which image, what a replica set needs, how its connection string is
spelled — live in `Backends.kt`, not in every library that talks to it. `shared-mongo` says what it
needs; it does not configure a `MongoDBContainer`.

```kotlin
fun mongoContainer(image: String = MONGO_IMAGE): ContainerService<MongoDBContainer> =
    ContainerService.declare(
        name = "mongodb",
        reusing = "MONGO_TEST_URI",
        create = { MongoDBContainer(DockerImageName.parse(image)) },
        endpointOf = MongoDBContainer::getReplicaSetUrl,
    )
```

Image tags match what the workspace already runs — `mongo:8` — so a machine that has the image pulls
nothing.

`MongoDBContainer` rather than a bare `GenericContainer` because it initiates a single-node replica
set, and `startTransaction` fails outright against a standalone `mongod`. That is verified, not
assumed: `shared-mongo`'s transaction specs pass against the container with `MONGO_TEST_URI` unset.

## Why this is not a `testFixtures` of each library

Because the next library needs the same three-way resolution, the same shutdown hook and the same
image pins, and the version of this that lives in two modules is the version that drifts. It is a
`jvm/lib` consumed through `test-dependencies`, so nothing here reaches a consumer of the libraries
it supports.
