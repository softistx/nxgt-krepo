---
"nxgt-krepo": patch
---

`stx-testing`: `minioContainer()` now defaults to `quay.io/minio/minio:latest`. MinIO no longer publishes
to Docker Hub, and the previous default, `minio/minio:latest`, fails to pull with "repository does not
exist" — so every spec that fell through to a container skipped or failed instead of running.
