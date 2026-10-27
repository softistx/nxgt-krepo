# stx-ktor

## 0.3.1

### Patch Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Documentation only: a provided `instance` is not closed by the plugin, but Ktor's DI still closes it at application stop. The KDoc and the `stx-ktor` README now say when that bites and how to replace `onShutdown` in `install(DI)`.

## 0.3.0

### Minor Changes

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `cors()` now honours `originPatterns` (a glob such as `https://*.example.com`) and refuses an origin written without its `scheme://`; `ContainerService.describe()` resolves the service before saying where it came from; the `stx.telemetry.version` KDoc no longer promises a jar-manifest fallback.

### Patch Changes

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The server dependencies move to their latest patch and minor releases: Ktor 3.6.0, the MongoDB driver 5.13.0, Kafka clients 4.4.0, the RabbitMQ client 5.37.0, Hibernate Validator 9.1.5 and KotlinPoet 2.4.0. Lettuce stays at 7.7 and kotlinx.serialization at the toolchain's 1.11.0.
- Updated dependencies [[`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f)]:
  - stx-common@0.3.0

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-ktor`.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
