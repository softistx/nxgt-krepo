# Local services

**Nothing here needs a service to be running, and nothing here finds one by guessing.** The
resolution order is in the next paragraph; its consequence is that a fresh clone with Docker starts
its own containers, a machine that exports the variables reuses long-running servers, and a machine
with neither skips those specs while everything else still runs.

A server you already have is therefore used only if you say so — a `mongod` on the default port is
invisible to a spec until `MONGO_TEST_URI` names it. The variable per backend is in stx-testing's `containers/` files
(`MONGO_TEST_URI`, `REDIS_TEST_URI`, `AMQP_TEST_URI`, …) and exporting one points every spec in the
repo at that server. Mongo is the one worth exporting on a development machine: it has to be an
`rs0` replica set, because `startTransaction` fails outright against a standalone `mongod`, and
initiating one per run is the slowest container start here.

**CI is the second case, and it has to be.** `ci.yml` starts one of each server, exports the
variables, and then runs the tests **one module at a time** — `./kotlin test -m` in a loop. Each of
those invocations is a fresh JVM that shares nothing with the last, so without the exports every one
of the test modules would start its own Postgres and its own Mongo. The two halves only work together.
Sharing a server is safe for data because every spec names its schema, database, key prefix, queue
or bucket through `TestNames`, whose run id differs per JVM. What the loop protects is the CPU. A bare
`./kotlin test` is about twice as fast, because the CLI then starts once, but it runs module JVMs as
wide as it likes and has no setting for it — and in two runs out of five that broke a bound that
proves a behaviour. **Such a bound is never loosened to buy speed**; a harness timeout, which proves
nothing, may be. `ci.yml` has the measurements.

**The examples that are applications run in Docker too**, on these same servers, from the
repository root's `compose.yaml`: a dev container, `workspace` (the checkout mounted, the IDE
attached through `.devcontainer/devcontainer.json`, any module run with `./kotlin run`), and an
`<example>-prod` profile per example (the executable jar baked into an image), on the external
`proxy` network, answering at `<example>.localhost` through Traefik. The workspace's servers
publish no host port, so for `jpa-shop` and `spring-orders` that is the way in.
`docs/examples-in-docker.md` has it.

**Check `docker ps` before pulling an image or starting a container.** The pull costs a gigabyte,
and a second container either clashes on the port or silently tests a different server than the one
everything else is pointed at.

**A spec must not depend on the host having the right daemon up.** `libs/core/stx-testing` declares
each backing service and resolves it in one order: the environment variable if it names a server,
otherwise a container started once for the run, otherwise `available == false` and the spec skips.
Mongo, Redis, AMQP and MinIO all work this way. Declare a new backend in stx-testing's `containers/` files, never in a
library's own test tree.

**A harness reaches the endpoint with `requireEndpoint()` and names its namespace with
`TestNames`.** Both live in `stx-testing` and both replaced a pattern that was written out eleven
times. `endpoint!!` past an `available` gate throws a `NullPointerException` naming a line, where
`requireEndpoint()` throws `describe()` — which of the three resolutions this was, and why. And
because a container is started once and *shared*, a spec's isolation is a database, schema, topic,
bucket or key prefix of its own inside it: `TestNames("stx_mongo_test", separator = "_").next()`
carries a per-run suffix, so a crashed run's leftovers cannot collide with the next run's names. The
thing it replaced was a prefix *sweep* before the first spec, which could not tell a crashed run's
databases from a concurrent run's and so had two suites deleting each other's data. Never reintroduce
a sweep; name what cannot collide. `libs/core/stx-testing/README.md` has both.

**A Spring application gets its MongoDB as a bean, not as a property.** `stx-spring-boot`'s
`com.softistx.spring.testing` ships `MongoSpec` — `@SpringBootTest` plus a `MongoConnectionDetails`
contributed over `stx-testing` — so no application writes a `@DynamicPropertySource` of its own. That
is not only about repetition: a property name can be wrong and say nothing, and one was. Boot 4 moved
the driver's settings from `spring.data.mongodb` to **`spring.mongodb`**, and `examples/spring-orders`
spent a phase talking to `mongodb://localhost/test` — the workspace's own replica set — with a
container running beside it and a green suite. A bean is asked for by type and cannot be misspelled.
`docs/spring-testing.md` has the four lines an application writes.

`spring.mongodb.database` there names a *prefix*, not a database: the test support appends the run
suffix — the `TestNames` rule above, reached through a `BeanPostProcessor` because
`reactiveMongoDatabaseFactory` reads the property before the connection string — and drops the result
at JVM exit. Two suites against the workspace replica set therefore cannot clear each other's
collections, and a run leaves it as it found it.

| library | override | without it |
| --- | --- | --- |
| `stx-mongo` | `MONGO_TEST_URI` | `mongo:8`, a single-node replica set |
| `stx-redis` | `REDIS_TEST_URI` | `redis:8-alpine`, on db 15 |
| `stx-amqp` | `AMQP_TEST_URI` | `rabbitmq:4-management` |
| `stx-storage` | `MINIO_TEST_ACCESS_KEY` **and** `..._SECRET_KEY` | `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` |
| `stx-kafka` | `KAFKA_TEST_BOOTSTRAP` | `confluentinc/cp-kafka:latest`, one broker |
| `stx-jpa` | `POSTGRES_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `postgres:18-alpine` |
| `stx-jpa` | `MYSQL_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `mysql:8.4` |
| `stx-jpa` | `DB2_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | nothing — the DB2 specs skip |

**The credentials rule is unchanged; what it costs is not.** `AMQP_TEST_URI` and the MinIO key pair
still have no defaults and must never gain any — a credential with a default is a credential in
source control. They are exported for a run, from wherever the server being reused keeps its own,
and never committed. But their absence is no longer a reason to skip: a container hands out credentials of its
own, so the 78 specs in those two libraries now run on a machine where nobody exported anything.
They used to report skipped there and prove nothing.

`MINIO_TEST_ENDPOINT` on its own does not take the override — an endpoint with no way in fails later
and less clearly than a container would. `POSTGRES_TEST_URI` is refused on its own for the same
reason: a Postgres URI does not carry the password, and a driver that connects without one fails at
authentication in a way that reads like a network problem.

**Each `stx-jpa` spec gets a schema of its own**, created before it and dropped `cascade` after
it — the per-spec Mongo database and Redis namespace, in the shape Postgres has for it. It earns its
keep against a real server: a spec creating its tables in `public` would be working among whatever
else lives there, and Hibernate's `create-drop` would take that with it on the way out. Any Postgres
a developer already runs is exactly such a server.

The reuse path is still the fast local loop, and still the seam CI uses to point at a service it
provisioned. A reused server is shared, so everything below about leaving it as you found it applies
to it exactly as before.

**Kafka's container is one broker, and that costs something worth knowing.** A production-shaped
cluster is three brokers with `min.insync.replicas = 2`, so a topic there has three replicas and
`acks = all` really waits for a quorum; a container gives one replica, so it waits for one broker.
The ack path is exercised either way, the quorum only on the real cluster. Nothing asks for a hard
three any more — `KafkaTestCluster.replicationFactor` asks the cluster what it has, capped at three,
because a topic asking for more replicas than there are brokers is not a weaker test but a refused
`createTopics`.

To exercise the quorum, point at a real cluster. Brokers in a compose network typically advertise
container hostnames and publish no host ports, so the names have to resolve first — an address the
host can reach is not enough on its own:

```
# /etc/hosts
172.22.0.115 kafka1
172.22.0.116 kafka2
172.22.0.117 kafka3
```

```bash
KAFKA_TEST_BOOTSTRAP="kafka1:9092,kafka2:9094,kafka3:9096" ./kotlin test -m stx-kafka
```

**An override that does not answer is not quietly replaced by a container.** Naming a cluster and
getting a container instead would be worse than skipping: the run would look green and would have
tested something else. This holds for all five backends.

To take the override and run against a broker or object store that is already up:

```bash
AMQP_TEST_URI="amqp://user:password@localhost:5672/%2F" ./kotlin test -m stx-amqp

MINIO_TEST_ACCESS_KEY=… MINIO_TEST_SECRET_KEY=… ./kotlin test -m stx-storage
```

The `%2F` there is the default virtual host and not decoration — a plain trailing `/` is the *empty*
vhost, which the broker refuses with a message about permissions that says nothing about the cause.
The container's URI carries no vhost path at all and sidesteps it.

A run that reuses a server has to leave it as it found it, because it is not theirs: the Mongo specs use a
database per spec and drop it, the Redis specs use database 15 with a key namespace per spec and
delete it, the storage specs create a bucket per scenario and empty and remove it, and the Kafka
specs create only the topics and groups they delete, and the AMQP specs name every exchange and
queue uniquely per run and delete them. Nothing here calls `FLUSHDB`, and nothing touches a bucket,
a topic or a queue it did not create.

Keep all of that even where a container made it unnecessary. Against a container, a spec that fails
to clean up after itself is a spec whose next run behaves differently — the isolation is what makes
that visible, and `FLUSHDB` is what would hide it.
