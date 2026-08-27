package com.strange.testing.containers

import com.github.dockerjava.api.command.InspectContainerResponse
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.kafka.ConfluentKafkaContainer
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
fun mongoContainer(image: String = MONGO_IMAGE): ContainerService<MongoDBContainer, String> =
    ContainerService.declare(
        name = "mongodb",
        reusing = "MONGO_TEST_URI",
        create = { MongoDBContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("mongo")) },
        endpointOf = MongoDBContainer::getReplicaSetUrl,
    )

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

/** Already on this machine, and the same server the workspace publishes on 9000. */
private const val MINIO_IMAGE = "minio/minio:latest"

/**
 * Where an object store is and what opens it — the three together, because two of them are useless
 * alone.
 */
data class MinioEndpoint(
    val url: String,
    val accessKey: String,
    val secretKey: String,
)

/**
 * MinIO, with the key pair it was started with.
 *
 * The only backend here that needs more than a URI, and the reason [ContainerService] resolves a
 * value rather than a string. The override is refused unless **both** keys are present:
 * `MINIO_TEST_ENDPOINT` on its own would otherwise send a run at somebody's real object store with
 * no way in, which fails later and less clearly than falling through to a container.
 *
 * `MINIO_TEST_ACCESS_KEY` and `MINIO_TEST_SECRET_KEY` still have no defaults, and still must not be
 * committed — they live in `~/workspace/docker/apps/minio/.env`. What has changed is that not having
 * them is no longer a reason to skip.
 */
fun minioContainer(image: String = MINIO_IMAGE): ContainerService<MinIOContainer, MinioEndpoint> =
    ContainerService.declare(
        name = "minio",
        reusing = "MINIO_TEST_ACCESS_KEY and MINIO_TEST_SECRET_KEY",
        fromEnvironment = {
            val access = System.getenv("MINIO_TEST_ACCESS_KEY")
            val secret = System.getenv("MINIO_TEST_SECRET_KEY")
            if (access.isNullOrBlank() || secret.isNullOrBlank()) {
                null
            } else {
                MinioEndpoint(System.getenv("MINIO_TEST_ENDPOINT") ?: "http://localhost:9000", access, secret)
            }
        },
        create = { MinIOContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("minio/minio")) },
        fromContainer = { MinioEndpoint(it.s3URL, it.userName, it.password) },
    )

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

/** The image the workspace runs, so a machine that has it pulls nothing. */
private const val POSTGRES_IMAGE = "postgres:18-alpine"

/**
 * Where a Postgres is, what opens it, and which database to open — a URI is not enough on its own.
 *
 * [uri] is spelled the way a reactive driver wants it, `postgresql://host:port/database`, and not as
 * the JDBC URL `PostgreSQLContainer` hands out: nothing here goes through JDBC.
 */
data class PostgresEndpoint(
    val uri: String,
    val username: String,
    val password: String,
    val database: String,
)

/**
 * PostgreSQL, with the credentials the container was started with.
 *
 * The second backend after MinIO that needs more than a URI, and for the same reason: a password is
 * not part of what the connection string carries here, and a driver asked to connect without one
 * fails at authentication rather than at connect, which reads like a network problem and is not.
 *
 * The override is refused unless all three of `POSTGRES_TEST_URI`, `POSTGRES_TEST_USER` and
 * `POSTGRES_TEST_PASSWORD` are set. A URI on its own would otherwise send a run at somebody's real
 * database with no way in — later and less clearly than falling through to a container.
 */
fun postgresContainer(image: String = POSTGRES_IMAGE): ContainerService<PostgreSQLContainer<*>, PostgresEndpoint> =
    ContainerService.declare(
        name = "postgres",
        reusing = "POSTGRES_TEST_URI, POSTGRES_TEST_USER and POSTGRES_TEST_PASSWORD",
        fromEnvironment = {
            val uri = System.getenv("POSTGRES_TEST_URI")
            val user = System.getenv("POSTGRES_TEST_USER")
            val password = System.getenv("POSTGRES_TEST_PASSWORD")
            if (uri.isNullOrBlank() || user.isNullOrBlank() || password.isNullOrBlank()) {
                null
            } else {
                PostgresEndpoint(uri, user, password, uri.substringAfterLast('/').substringBefore('?'))
            }
        },
        create = { PostgreSQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres")) },
        fromContainer = {
            PostgresEndpoint(
                uri = "postgresql://${it.host}:${it.getMappedPort(POSTGRES_PORT)}/${it.databaseName}",
                username = it.username,
                password = it.password,
                database = it.databaseName,
            )
        },
    )

private const val POSTGRES_PORT = 5432

/** MySQL 8.4 is the current LTS, and the one a deployment is most likely to be on. */
private const val MYSQL_IMAGE = "mysql:8.4"

/**
 * Where a MySQL is, what opens it, and which database to open.
 *
 * [uri] is the reactive spelling, `mysql://host:port/database` — the Vert.x client's, not JDBC's.
 */
data class MysqlEndpoint(
    val uri: String,
    val username: String,
    val password: String,
    val database: String,
)

/**
 * MySQL, with the credentials the container was started with.
 *
 * The same three-variable override rule as Postgres, for the same reason: a URI without credentials
 * would send a run at somebody's real server with no way in.
 *
 * **`root`, and only because a spec has to create a database of its own.** The container's ordinary
 * user has rights on one database and no right to make another, and a spec that instead used the
 * database it was given would be one `create-drop` away from emptying it — which is exactly the
 * accident the per-spec database exists to prevent. On a server named through the environment the
 * account has to be able to `create database` for the same reason, and failing loudly when it cannot
 * is the right outcome.
 */
fun mysqlContainer(image: String = MYSQL_IMAGE): ContainerService<MySQLContainer<*>, MysqlEndpoint> =
    ContainerService.declare(
        name = "mysql",
        reusing = "MYSQL_TEST_URI, MYSQL_TEST_USER and MYSQL_TEST_PASSWORD",
        fromEnvironment = {
            val uri = System.getenv("MYSQL_TEST_URI")
            val user = System.getenv("MYSQL_TEST_USER")
            val password = System.getenv("MYSQL_TEST_PASSWORD")
            if (uri.isNullOrBlank() || user.isNullOrBlank() || password.isNullOrBlank()) {
                null
            } else {
                MysqlEndpoint(uri, user, password, uri.substringAfterLast('/').substringBefore('?'))
            }
        },
        create = { ReactiveMySQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("mysql")) },
        fromContainer = {
            MysqlEndpoint(
                uri = "mysql://${it.host}:${it.getMappedPort(MYSQL_PORT)}/${it.databaseName}",
                username = "root",
                password = it.password,
                database = it.databaseName,
            )
        },
    )

private const val MYSQL_PORT = 3306

/**
 * A MySQL container that waits on the log rather than on a JDBC connection.
 *
 * `JdbcDatabaseContainer.waitUntilContainerStarted` decides the container is up by opening a JDBC
 * connection to it, which needs `com.mysql.cj.jdbc.Driver` on the classpath. Nothing in this repo
 * speaks JDBC — `shared-jpa` is reactive from the driver up — so that probe throws *"Could not get
 * JDBC Driver"*, `ContainerService` catches it, and every MySQL spec skips without saying why.
 *
 * `PostgreSQLContainer` overrides the same method for its own reasons, which is why Postgres has
 * never needed this. Overriding it here costs six lines against a two-megabyte driver added solely
 * to answer "are you listening yet". The log line is MySQL's own, and it says it twice: once for the
 * initialisation server it starts to build the data directory, and once for the real one.
 */
private class ReactiveMySQLContainer(
    image: DockerImageName,
) : MySQLContainer<ReactiveMySQLContainer>(image) {
    init {
        waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
        // Enables a plugin 8.4 ships disabled. It is not the default for anything here — it is what
        // makes the ALTER below legal, and that ALTER is the point.
        withCommand("--mysql-native-password=ON")
    }

    override fun waitUntilContainerStarted() {
        waitStrategy.waitUntilReady(this)
    }

    /**
     * Moves `root` onto `mysql_native_password`, because the reactive client cannot authenticate
     * against the default.
     *
     * MySQL 8.4 creates every account with `caching_sha2_password`. Its first authentication for a
     * given account is a *full* one, which sends the password in a form that requires either TLS the
     * client trusts or the server's RSA public key — and the container's certificate is self-signed,
     * so a client that has not been told to trust it drops the connection. The symptom is
     * `ClosedConnectionException: Failed to read any response from the server`, which reads like a
     * network fault and is an authentication one.
     *
     * Done through `mysql` inside the container rather than over the wire, precisely because nothing
     * here can get a connection yet. The retry is because MySQL logs *ready for connections* twice
     * during initialisation and is briefly not, in fact, ready.
     */
    override fun containerIsStarted(info: InspectContainerResponse) {
        val alter = "ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '$password'"
        repeat(ATTEMPTS) { attempt ->
            val result = runCatching { execInContainer("mysql", "-uroot", "-p$password", "-e", alter) }
            if (result.getOrNull()?.exitCode == 0) return
            check(attempt < ATTEMPTS - 1) { "could not switch the MySQL root account to mysql_native_password" }
            Thread.sleep(INTERVAL_MILLIS)
        }
    }

    private companion object {
        const val ATTEMPTS = 60
        const val INTERVAL_MILLIS = 500L
    }
}

/**
 * Where a DB2 is, if anyone said — and null if nobody did.
 *
 * **The one backend here with no container fallback.** `icr.io/db2_community/db2` needs a privileged
 * container, several gigabytes and minutes to become ready, and this repo's rule is that a test run
 * does not put the machine under that kind of load unasked. So DB2 is reused or skipped, never
 * started: `shared-jpa` ships the driver, and the specs that would exercise it run only where a
 * server is named.
 */
data class Db2Endpoint(
    val uri: String,
    val username: String,
    val password: String,
    val database: String,
)

/** The DB2 named by `DB2_TEST_URI`, `DB2_TEST_USER` and `DB2_TEST_PASSWORD`, or null. */
fun db2Endpoint(): Db2Endpoint? {
    val uri = System.getenv("DB2_TEST_URI")
    val user = System.getenv("DB2_TEST_USER")
    val password = System.getenv("DB2_TEST_PASSWORD")
    return if (uri.isNullOrBlank() || user.isNullOrBlank() || password.isNullOrBlank()) {
        null
    } else {
        Db2Endpoint(uri, user, password, uri.substringAfterLast('/').substringBefore('?'))
    }
}
