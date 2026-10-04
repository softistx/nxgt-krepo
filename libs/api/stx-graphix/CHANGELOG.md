# stx-graphix

## 0.2.2

### Patch Changes

- [#241](https://github.com/softistx/nxgt-krepo/pull/241) [`8ddf228`](https://github.com/softistx/nxgt-krepo/commit/8ddf228d16399e5f538ec663fb24e87ff5ae53cc) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Schema documents under a `classpath:` location are now found inside a jar the class loader names
  and `java.class.path` does not — the `BOOT-INF/classes/` of a Spring Boot executable jar, which is
  what `./kotlin package -f executable-jar` writes. Such an application used to start with no
  documents at all and fall back to building the schema from its types, which fails as soon as a
  document declared something types cannot say, such as a `union`.

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-graphix`, `io.github.softistx:stx-graphix-koin`, `io.github.softistx:stx-graphix-ktor` and `io.github.softistx:stx-graphix-spring` — the **stx-graphix** family. They carry one version between them and ship together.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
