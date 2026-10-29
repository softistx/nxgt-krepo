# stx-testing

## 0.2.4

### Patch Changes

- [#408](https://github.com/softistx/nxgt-krepo/pull/408) [`dfe5536`](https://github.com/softistx/nxgt-krepo/commit/dfe5536d85981360e32019446227906de11ff1e6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Move the internal `Registry` and `Message` types into their own files (`Registry.kt`, `Message.kt`). No change to the public API or behaviour.

## 0.2.3

### Patch Changes

- [#402](https://github.com/softistx/nxgt-krepo/pull/402) [`4ce4632`](https://github.com/softistx/nxgt-krepo/commit/4ce4632ad6bcc831728ab65f42fde9769e188e55) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Split `Backends.kt` into one file per backend (`MongoContainer.kt`, `PostgresEndpoint.kt`, `Db2Endpoint.kt`, ...) with the three-variable environment override of Postgres, MySQL and DB2 read by one shared helper. No change to the public API or to any environment variable.

## 0.2.2

### Patch Changes

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `cors()` now honours `originPatterns` (a glob such as `https://*.example.com`) and refuses an origin written without its `scheme://`; `ContainerService.describe()` resolves the service before saying where it came from; the `stx.telemetry.version` KDoc no longer promises a jar-manifest fallback.

- [#239](https://github.com/softistx/nxgt-krepo/pull/239) [`27957c3`](https://github.com/softistx/nxgt-krepo/commit/27957c311fa8e51a744f567be27a41ed7a88d383) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `minioContainer()` now starts `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` instead of
  `quay.io/minio/minio:latest`, which since 2026-10 refuses an anonymous pull with `unauthorized` — so
  every spec that fell through to a container failed before it ran. It is the upstream MinIO server
  built from the same source; pass `image =` to choose another.

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-testing`.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
