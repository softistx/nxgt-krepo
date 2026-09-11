# stx-testing

Test-only support the libraries here share. Today: the backing services their integration specs
talk to, declared rather than assumed.

**`io.github.softistx:stx-testing`** — [how to depend on it](../../docs/consuming.md).

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

## Two conventions every harness follows

A container is started **once** and shared, so the two things a spec does with it — ask for its
address, and carve out something of its own inside it — are the same two lines in eleven modules.
They are one shape each here rather than eleven.

**`requireEndpoint()`, never `endpoint!!`.** Past an `available` gate the endpoint is there, and the
question is what happens on the day it is not. `!!` answers with a `NullPointerException` naming a
line; this answers with `describe()` — *"mongodb: unavailable — MONGO_TEST_URI is unset and Docker is
not reachable"*, or the exception the container start threw. That distinction is the entire reason
`describe()` exists, and 29 call sites were throwing it away.

**`TestNames` for the namespace a spec owns.** A database, a schema, a topic, a bucket, a key prefix:

```kotlin
private val databases = TestNames("stx_mongo_test", separator = "_")

val database = client.getDatabase(databases.next())
```

The name carries a per-run suffix, and that is the load-bearing half. A plain counter restarts at 1
in the next JVM, so a run that crashed leaves `stx_mongo_test_1` populated and the next run's first
insert fails on a duplicate `_id`. This repo shipped that bug and patched it by *sweeping* every
database matching the prefix before the first spec — which cannot tell a crashed run's leftovers from
a **concurrent** run's, and so two suites at once deleted each other's data. A name that cannot
collide needs no sweep, and every sweep here is gone.

The trade is that a crashed run leaves its namespace behind on a server `MONGO_TEST_URI` names. That
is the cheap side: against a container, which is the default, the server itself is thrown away at JVM
exit. `separator` is a parameter because the namespaces disagree — `_` for SQL identifiers and Mongo
databases, `-` for topics and buckets, `:` for Redis keys.

## The backends

| | image | override | resolved value |
| --- | --- | --- | --- |
| `mongoContainer()` | `mongo:8` | `MONGO_TEST_URI` | the replica-set URI |
| `redisContainer()` | `redis:8-alpine` | `REDIS_TEST_URI` | `redis://host:port/15` |
| `rabbitContainer()` | `rabbitmq:4-management` | `AMQP_TEST_URI` | `amqp://user:pass@host:port` |
| `minioContainer()` | `minio/minio:latest` | `MINIO_TEST_ACCESS_KEY` **and** `..._SECRET_KEY` | `MinioEndpoint(url, accessKey, secretKey)` |
| `kafkaContainer()` | `confluentinc/cp-kafka:latest` | `KAFKA_TEST_BOOTSTRAP` | the bootstrap servers |
| `postgresContainer()` | `postgres:18-alpine` | `POSTGRES_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `PostgresEndpoint(uri, username, password, database)` |
| `mysqlContainer()` | `mysql:8.4` | `MYSQL_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `MysqlEndpoint(uri, username, password, database)` |
| `db2Endpoint()` | none — reused or skipped | `DB2_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `Db2Endpoint(uri, username, password, database)`, or `null` |

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
fun mongoContainer(image: String = MONGO_IMAGE): ContainerService<MongoDBContainer, String> =
    ContainerService.declare(
        name = "mongodb",
        reusing = "MONGO_TEST_URI",
        create = { MongoDBContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("mongo")) },
        endpointOf = MongoDBContainer::getReplicaSetUrl,
    )
```

Two parameters, not one: the second is what the endpoint *resolves to*. Most backends answer with a
URI and take the `endpointOf` overload above; MinIO, Postgres and MySQL need credentials alongside
the address, so they take the `fromEnvironment`/`fromContainer` one and answer with a data class.
`asCompatibleSubstituteFor` is what lets the image be overridden — a pinned digest, or a mirror —
without Testcontainers refusing a name it does not recognise.

Image tags match what the workspace already runs — `mongo:8` — so a machine that has the image pulls
nothing.

`MongoDBContainer` rather than a bare `GenericContainer` because it initiates a single-node replica
set, and `startTransaction` fails outright against a standalone `mongod`. That is verified, not
assumed: `stx-mongo`'s transaction specs pass against the container with `MONGO_TEST_URI` unset.

## Using one from a Spring application

Not directly. `stx-spring-boot`'s `com.softistx.spring.testing` wraps `mongoContainer()` in a
`MongoConnectionDetails` bean and a `MongoSpec` base class, so an application's specs never name a
container or a property — they extend `MongoSpec` and say which database they want in
`application-test.yaml`. That is the same three-way resolution as everything here; what it adds is
the one thing this module cannot know, which is how Spring is told.

## Why this is not a `testFixtures` of each library

Because the next library needs the same three-way resolution, the same shutdown hook and the same
image pins, and the version of this that lives in two modules is the version that drifts. It is a
`jvm/lib` consumed through `test-dependencies`, so nothing here reaches a consumer of the libraries
it supports.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
