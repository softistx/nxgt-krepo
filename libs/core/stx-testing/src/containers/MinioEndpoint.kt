package com.softistx.testing.containers

import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName

/**
 * Pigsty's build of the upstream MinIO server, pinned. MinIO stopped publishing images: `minio/minio`
 * no longer pulls at all, and since 2026-10 `quay.io/minio/minio` refuses an anonymous pull with
 * `unauthorized`. `pgsty/minio` is the same binary built from the same source — same `server /data`,
 * same `MINIO_ROOT_*` — so the container below needs no change. Pinned to a release rather than
 * `latest`, because a community rebuild is exactly where an unannounced change would come from.
 * The compatible-substitute call below is what lets Testcontainers accept a name that is not
 * `minio/minio`.
 */
private const val MINIO_IMAGE = "pgsty/minio:RELEASE.2026-08-04T00-00-00Z"

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
 * committed — they are exported for a run, from wherever the store being reused keeps its own. What
 * has changed is that not having them is no longer a reason to skip.
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
