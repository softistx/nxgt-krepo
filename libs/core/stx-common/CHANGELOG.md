# stx-common

## 0.3.0

### Minor Changes

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - A lease or lock renewal that throws is reported and retried, and the guarded block is cancelled with a `LeaseLostException` once no renewal has succeeded within the duration (the same for `RedisLock`'s watchdog), `CorsPolicy.validate` refuses an origin written without its `scheme://` for Spring as well as Ktor, a cancelled `KeyedMutex` caller or `RedisLock` holder still gives its place back, `RedisStream.history(count)` returns the last entries, and `RedisCache.putAll` writes as one batch.

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Keyset paging no longer drops documents whose sort field is null, missing or a dotted path, a page size is capped at 1000 (`?size=` is coerced down to it), the Mongo audit trail records the signed-in author, writes entries in save order and refuses a duplicate version, and an upstream's unlisted status code such as 522 no longer throws.

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-common`.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
