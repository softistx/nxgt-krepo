# nxgt-krepo

## 0.2.1

### Patch Changes

- [#216](https://github.com/softistx/nxgt-krepo/pull/216) [`e391075`](https://github.com/softistx/nxgt-krepo/commit/e3910753d501327282cedcc0c2d233b87beb6e89) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Specs only, nothing published changes: two harness timeouts now allow a slow start when CI runs every module's tests at once — `stx-graphix-spring`'s WebTestClient and the Ktor plugin specs' module-loading limit.

- [#219](https://github.com/softistx/nxgt-krepo/pull/219) [`4eecdef`](https://github.com/softistx/nxgt-krepo/commit/4eecdefc3e95f762f991beaee4200ba700899f63) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The libraries now live in role folders — `libs/core`, `libs/data`, `libs/messaging`, `libs/api`, `libs/observability`, `libs/ui` — so that ownership is readable from the tree. No coordinate changes: every artifact keeps its `io.github.softistx:stx-*` name, because a module's name is still its own directory name.

## 0.2.0

### Minor Changes

- [#205](https://github.com/softistx/nxgt-krepo/pull/205) [`618314b`](https://github.com/softistx/nxgt-krepo/commit/618314be0e4e9712e8476653e20bf80b26c10da9) Thanks [@SteveGT96](https://github.com/SteveGT96)! - **Breaking: the Maven group is now `io.github.softistx`, not `com.softistx`.** Update every
  coordinate — `io.github.softistx:stx-jpa:…`. The Kotlin package prefix is unchanged: imports stay
  `com.softistx.*`. Nothing had been published under the old group, so no released artifact moves.
  
  Every artifact now publishes a **sources jar**, and its POM carries the project URL, SCM, Apache-2.0
  and a developer — so an IDE shows the source, and the metadata Maven Central requires is already
  there.
  
  The libraries are released under **Apache-2.0** and published to **Maven Central**: no repository
  block, no credentials, PGP-signed. `docs/consuming.md` has the Gradle, Maven and Kotlin Toolchain
  forms.

### Patch Changes

- [#214](https://github.com/softistx/nxgt-krepo/pull/214) [`b915b19`](https://github.com/softistx/nxgt-krepo/commit/b915b196a74c9113186504e1caedee5cf9549165) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `stx-testing`: `minioContainer()` now defaults to `quay.io/minio/minio:latest`. MinIO no longer publishes
  to Docker Hub, and the previous default, `minio/minio:latest`, fails to pull with "repository does not
  exist" — so every spec that fell through to a container skipped or failed instead of running.
