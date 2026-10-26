# stx-graphix

## 0.2.2

### Patch Changes

- [#241](https://github.com/softistx/nxgt-krepo/pull/241) [`8ddf228`](https://github.com/softistx/nxgt-krepo/commit/8ddf228d16399e5f538ec663fb24e87ff5ae53cc) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Schema documents under a `classpath:` location are now found inside a jar the class loader names
  and `java.class.path` does not — the `BOOT-INF/classes/` of a Spring Boot executable jar, which is
  what `./kotlin package -f executable-jar` writes. Such an application used to start with no
  documents at all and fall back to building the schema from its types, which fails as soon as a
  document declared something types cannot say, such as a `union`.

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The server dependencies move to their latest patch and minor releases: Ktor 3.6.0, the MongoDB driver 5.13.0, Kafka clients 4.4.0, the RabbitMQ client 5.37.0, Hibernate Validator 9.1.5 and KotlinPoet 2.4.0. Lettuce stays at 7.7 and kotlinx.serialization at the toolchain's 1.11.0.

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - kotlin-reflect moves to 2.4.20, matching the Kotlin stdlib the toolchain ships, and classgraph (classpath scanning in stx-jpa) moves from 4.8 to 6.0.0, a major release of that dependency that needs JDK 25, so a consumer that calls classgraph 4 APIs directly has to move too.

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - A GraphQL mutation sent over GET is now refused with HTTP 405 and `Allow: POST` in both the Ktor and Spring integrations, a graphql-ws operation that finishes no longer drops a new subscription reusing its id, `shutdown()` is safe against concurrent add and remove, and the Spring integration's router, sandbox and graphql-ws beans can each be replaced by a bean of the same name.
- Updated dependencies [[`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f), [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f)]:
  - stx-common@0.3.0

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-graphix`, `io.github.softistx:stx-graphix-koin`, `io.github.softistx:stx-graphix-ktor` and `io.github.softistx:stx-graphix-spring` — the **stx-graphix** family. They carry one version between them and ship together.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
