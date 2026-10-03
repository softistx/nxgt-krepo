---
"stx-testing": patch
---

`minioContainer()` now starts `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` instead of
`quay.io/minio/minio:latest`, which since 2026-10 refuses an anonymous pull with `unauthorized` — so
every spec that fell through to a container failed before it ran. It is the upstream MinIO server
built from the same source; pass `image =` to choose another.
