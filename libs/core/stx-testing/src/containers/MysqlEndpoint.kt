package com.softistx.testing.containers

import com.github.dockerjava.api.command.InspectContainerResponse
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

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
fun mysqlContainer(image: String = MYSQL_IMAGE): ContainerService<MySQLContainer, MysqlEndpoint> =
    ContainerService.declare(
        name = "mysql",
        reusing = "MYSQL_TEST_URI, MYSQL_TEST_USER and MYSQL_TEST_PASSWORD",
        fromEnvironment = { endpointFromEnvironment("MYSQL", ::MysqlEndpoint) },
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
 * speaks JDBC — `stx-jpa` is reactive from the driver up — so that probe throws *"Could not get
 * JDBC Driver"*, `ContainerService` catches it, and every MySQL spec skips without saying why.
 *
 * `PostgreSQLContainer` overrides the same method for its own reasons, which is why Postgres has
 * never needed this. Overriding it here costs six lines against a two-megabyte driver added solely
 * to answer "are you listening yet". The log line is MySQL's own, and it says it twice: once for the
 * initialisation server it starts to build the data directory, and once for the real one.
 */
private class ReactiveMySQLContainer(
    image: DockerImageName,
) : MySQLContainer(image) {
    init {
        waitingFor(Wait.forLogMessage(".*ready for connections.*", 2))
        // MySQL initialises a data directory before it serves anything, and the default minute is
        // not enough for that plus an image start when a full-suite run has several backends coming
        // up at once. Being slow here reads as being absent, which is a skip and not a failure.
        withStartupTimeout(Duration.ofMinutes(3))
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
     * **A freshly started MySQL says the same words for a different reason**, dropping the first
     * connection it is offered even after this has run. The two are told apart by trying again — an
     * authentication failure fails again — which is why `MySqlTestDatabase` probes more than once
     * rather than skipping on the first refusal.
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
