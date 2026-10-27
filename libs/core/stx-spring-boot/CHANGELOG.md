# stx-spring-boot

## 0.4.0

### Minor Changes

- [#321](https://github.com/softistx/nxgt-krepo/pull/321) [`18abc6f`](https://github.com/softistx/nxgt-krepo/commit/18abc6ff525567c44b66049f578785f0cc675da4) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `parseFilter`, `toSort`, `mongoPage` and the new `mongoFilter(allowed)` / `mongoQuery(allowed)` (for `find` routes; the properties of those names stay the open case) take an optional `allowed: Set<String>`, the fields a client may name in `?filter=` and `?sort=`. A field outside it is refused as `filters.invalid`, for a sort as well as a filter; dotted paths are matched whole. Left null, every field stays reachable, so `?filter=passwordHash:like:a` remains an oracle: set it on any route over a collection with a field that must not leak.
  
  The defaulted `allowed` parameter changes the JVM signatures of `parseFilter`, `toSort` and `mongoPage`, so consumers must recompile (source-compatible).

### Patch Changes

- Updated dependencies []:
  - stx-i18n@0.3.1

## 0.3.0

### Minor Changes

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Keyset paging no longer drops documents whose sort field is null, missing or a dotted path, a page size is capped at 1000 (`?size=` is coerced down to it), the Mongo audit trail records the signed-in author, writes entries in save order and refuses a duplicate version, and an upstream's unlisted status code such as 522 no longer throws.

### Patch Changes

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - kotlin-reflect moves to 2.4.20, matching the Kotlin stdlib the toolchain ships, and classgraph (classpath scanning in stx-jpa) moves from 4.8 to 6.0.0, a major release of that dependency that needs JDK 25, so a consumer that calls classgraph 4 APIs directly has to move too.

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `AuditListener` no longer fails Spring's class introspection when Spring Security is not on the classpath.
- Updated dependencies [[`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6), [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f)]:
  - stx-i18n@0.3.0
  - stx-common@0.3.0

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-spring-boot`.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
