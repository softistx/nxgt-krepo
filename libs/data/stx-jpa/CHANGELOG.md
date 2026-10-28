# stx-jpa

## 0.3.1

### Patch Changes

- [#387](https://github.com/softistx/nxgt-krepo/pull/387) [`b2b5e84`](https://github.com/softistx/nxgt-krepo/commit/b2b5e84ed7b9de19be22acdc692c5f660f04a943) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Move reference sections of the README into docs/.

## 0.3.0

### Minor Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - BREAKING: `findPage` throws `IllegalArgumentException`, naming the attribute, when its query fetches a collection. `limit` and `offset` cut the joined rows, so such a page truncated owners and collections and reported `hasNextPage = false`. Page the owners and load their collections separately, or project.

### Patch Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Documentation only: a provided `instance` is not closed by the plugin, but Ktor's DI still closes it at application stop. The KDoc and the `stx-ktor` README now say when that bites and how to replace `onShutdown` in `install(DI)`.

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Documents and pins `JpaSpec.or` with a side that restricts nothing: it keeps the other side alone, and the docs now show how to get "no restriction" instead.
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

This is the release line for `io.github.softistx:stx-jpa`, `io.github.softistx:stx-jpa-ktor` and `io.github.softistx:stx-jpa-spring` — the **stx-jpa** family. They carry one version between them and ship together.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
