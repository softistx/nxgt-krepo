# stx-migrations

## 0.2.4

### Patch Changes

- [#339](https://github.com/softistx/nxgt-krepo/pull/339) [`1007a7f`](https://github.com/softistx/nxgt-krepo/commit/1007a7f028e9902b62806e5902310ff0a49d486c) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Internal: `SqlMigrationLedger` is split by concern (lock, rows, writes). No behaviour or public API change.

## 0.2.3

### Patch Changes

- Updated dependencies [[`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4)]:
  - stx-ktor@0.3.1

## 0.2.2

### Patch Changes

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The server dependencies move to their latest patch and minor releases: Ktor 3.6.0, the MongoDB driver 5.13.0, Kafka clients 4.4.0, the RabbitMQ client 5.37.0, Hibernate Validator 9.1.5 and KotlinPoet 2.4.0. Lettuce stays at 7.7 and kotlinx.serialization at the toolchain's 1.11.0.

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - kotlin-reflect moves to 2.4.20, matching the Kotlin stdlib the toolchain ships, and classgraph (classpath scanning in stx-jpa) moves from 4.8 to 6.0.0, a major release of that dependency that needs JDK 25, so a consumer that calls classgraph 4 APIs directly has to move too.

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `jpa.connection { }` now borrows, runs and closes on one Vert.x context, the migration ledger no longer leaves a record `RUNNING` when its description is longer than the column, `findPage(limit)` refuses a limit above `MAX_PAGE_SIZE` instead of overflowing on `limit + 1`, and the runtime POMs move to Hibernate Reactive 4.5.12, Hibernate ORM 7.4.13 and Vert.x 5.1.10.
- Updated dependencies [[`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f)]:
  - stx-common@0.3.0
  - stx-ktor@0.3.0

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-migrations`, `io.github.softistx:stx-migrations-db`, `io.github.softistx:stx-migrations-ktor` and `io.github.softistx:stx-migrations-spring` — the **stx-migrations** family. They carry one version between them and ship together.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
