# stx-testing

## 0.2.2

### Patch Changes

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
