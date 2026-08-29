# stx-testing

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

The hook is a **virtual thread**, and an `unstarted` one. `Runtime.addShutdownHook` takes a `Thread`,
which is the only reason there is a thread in this module at all — nothing here suspends. Given one
is forced, it is virtual. And it must be built with `Thread.ofVirtual().unstarted { … }`, never
`Thread.startVirtualThread`: that starts on the spot, so the hook is registered already-terminated
and never runs, or is refused as still-alive and takes the whole registry's initialisation down with
it. The JVM swallows both. This module shipped with exactly that bug and nothing noticed, because
Ryuk was doing all the cleaning; three specs in `ContainerServiceTest` now pin it.

## The backends

| | image | override | resolved value |
| --- | --- | --- | --- |
| `mongoContainer()` | `mongo:8` | `MONGO_TEST_URI` | the replica-set URI |
| `redisContainer()` | `redis:8-alpine` | `REDIS_TEST_URI` | `redis://host:port/15` |
| `rabbitContainer()` | `rabbitmq:4-management` | `AMQP_TEST_URI` | `amqp://user:pass@host:port` |
| `minioContainer()` | `minio/minio:latest` | `MINIO_TEST_ACCESS_KEY` **and** `..._SECRET_KEY` | `MinioEndpoint(url, accessKey, secretKey)` |
| `kafkaContainer()` | `confluentinc/cp-kafka:latest` | `KAFKA_TEST_BOOTSTRAP` | the bootstrap servers |

**Kafka's container is one broker**, where the workspace cluster is three with
`min.insync.replicas = 2`. So `acks = all` waits for a quorum there and for one broker here — the
ack path is exercised either way, the quorum only on the real cluster.
`KafkaTestCluster.replicationFactor` asks the cluster what it has rather than assuming, because a
topic asking for three replicas on a one-broker cluster is a refused `createTopics`, not a weaker
test. Three brokers in containers would be faithful and cost ~3 GiB and half a minute per run.

**A container ends the credentials argument.** `AMQP_TEST_URI` and the MinIO key pair have no
defaults and never will — a credential with a default is a credential in source control — so before
this, 78 specs across those two libraries skipped on any machine where nobody had exported them, and
proved nothing there. A container has credentials of its own to hand out. The variables stay, as
overrides, with no defaults; what went is the reason to skip.

MinIO is why the resolved value is generic rather than a string: a URL without a key pair opens
nothing, so all three resolve together or not at all. `MINIO_TEST_ENDPOINT` on its own is
deliberately *not* enough to take the override — pointing a run at somebody's real object store with
no way in fails later, and less clearly, than starting a container.

Redis has no published Testcontainers module, so it is a `GenericContainer` waiting on its port. It
does not need Redis Stack, which is what the workspace runs: the specs use only core commands.

## Declaring a backend

A backend's particulars — which image, what a replica set needs, how its connection string is
spelled — live in `Backends.kt`, not in every library that talks to it. `stx-mongo` says what it
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
assumed: `stx-mongo`'s transaction specs pass against the container with `MONGO_TEST_URI` unset.

## Why this is not a `testFixtures` of each library

Because the next library needs the same three-way resolution, the same shutdown hook and the same
image pins, and the version of this that lives in two modules is the version that drifts. It is a
`jvm/lib` consumed through `test-dependencies`, so nothing here reaches a consumer of the libraries
it supports.
