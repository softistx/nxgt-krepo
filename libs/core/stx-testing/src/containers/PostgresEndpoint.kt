package com.softistx.testing.containers

import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

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
fun postgresContainer(image: String = POSTGRES_IMAGE): ContainerService<PostgreSQLContainer, PostgresEndpoint> =
    ContainerService.declare(
        name = "postgres",
        reusing = "POSTGRES_TEST_URI, POSTGRES_TEST_USER and POSTGRES_TEST_PASSWORD",
        fromEnvironment = { endpointFromEnvironment("POSTGRES", ::PostgresEndpoint) },
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
