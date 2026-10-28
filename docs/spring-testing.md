# Testing an application built on stx-spring-boot

The `com.softistx.spring.testing` package: what an application writes, and why each piece of the
harness is built the way it is. The module's own reasoning is in
[its README](../libs/core/stx-spring-boot/README.md); the keys are in
[`spring-configuration.md`](spring-configuration.md).

`com.softistx.spring.testing` is in `src/`, not in a test tree, because it is *for consumers* — an
application adds `//libs/core/stx-spring-boot` and `//libs/core/stx-testing` to its `test-dependencies` and
writes four things:

```kotlin
// test/ProjectConfig.kt — in package io.kotest.provided, where Kotest looks for it
object ProjectConfig : SpringProjectConfig()

// test/OrderControllerTest.kt — no bootstrap, no base spec of its own
class OrderControllerTest(template: ReactiveMongoTemplate, json: Json) : MongoSpec({
    beforeEach { template.clear("orders", "audits") }

    feature("POST /orders").config(enabled = mongoAvailable) { … }
})
```

```yaml
# testResources/application-test.yaml
server:
  port: 8088                       # TestServer.STX_TEST_PORT, so a client needs no URL spelled out
spring:
  mongodb:
    database: spring_orders_test   # a prefix: `testDatabase` appends this run's suffix
```

That is the whole surface. `MongoSpec` is `@ActiveProfiles("test")` plus
`@SpringBootTest(DEFINED_PORT)` plus the two `@TestConfiguration`s below; re-annotating a subclass
overrides any of them. `SpringSpec` is the same without Mongo, for an application on something else.

**Where MongoDB is, answered as a bean.** `MongoTestConfiguration` contributes a
`MongoConnectionDetails`, and `MongoReactiveAutoConfiguration` declares its own under
`@ConditionalOnMissingBean` of that type — so the test bean wins outright, with no property registry
and no `@DynamicPropertySource` companion for every application to copy. The URI comes from
`stx-testing`: `MONGO_TEST_URI` when a server is already up, a container started once for the run
otherwise, and neither means `mongoAvailable` is `false` and every feature gated on it reports
skipped rather than red.

**A bean rather than a property, and the reason is a scar.** Spring Boot 4 split the old
`spring.data.mongodb`: the driver's URI, credentials and database are `MongoProperties` under
**`spring.mongodb`**, while `spring.data.mongodb` kept only GridFS and the big-decimal
representation. A `spring.data.mongodb.uri` carried over from Boot 3 therefore binds to nothing, is
reported by nothing, and leaves the driver on `MongoProperties.DEFAULT_URI` — `mongodb://localhost/test`,
which on a developer machine is a real server that answers. `examples/spring-orders` was doing
exactly that, container and all, and its suite passed the whole time. A bean is asked for by type and
cannot be misspelled; `MongoSpecTest` pins the prefix so the next rename fails loudly.

**The database is the run's, and it is given back.** What `application-test.yaml` names is a prefix:
`testDatabase` appends `stx-testing`'s run suffix, and a `BeanPostProcessor` rewrites
`MongoProperties.database` with the result before anything reads it. Both halves are load-bearing.
The suffix is the convention every harness here follows — a fixed name on a server
`MONGO_TEST_URI` points at is shared with whatever else is running, and these specs empty
collections, so two suites at once would clear each other's; a suite run twice would also find its
migrations already in the ledger and never re-seed what the first run cleared. And the *post-processor*
rather than a name spliced into the URI alone, because
`DataMongoReactiveAutoConfiguration.reactiveMongoDatabaseFactory` reads `MongoProperties.getDatabase()`
first and only falls back to the connection string — so the URI-only version left the driver on one
database and `ReactiveMongoTemplate` on another, which is how `MongoSpecTest` found it. Then
`MongoTestCleanup` drops that database at JVM exit, as a shutdown hook and not a bean's destroy
method: the database belongs to the run, and Spring's context cache may close one context while
another is still writing. Against a container it costs a connection and nothing else; against the
workspace's own replica set it is the difference between leaving one database per run behind and
leaving none.

**The port is not a constant anybody keeps in sync.** `TestServer.port` starts at
`STX_TEST_PORT` — what `DEFINED_PORT` binds and what a spec body reads before any context exists —
and `TestServerConfiguration` overwrites it with the port the server actually took, so a subclass
re-annotated `RANDOM_PORT` works too. `webTestClient()` and an application's own client factory
default to it. `bindToServer` and not `bindToApplicationContext`: the kotlinx codecs, the exception
advice, the locale negotiation and the `Instant` converters are auto-configurations, and a client
bound to a context bypasses several of them.

**`extensions` is `final` on `SpringProjectConfig`.** The failure that prevents is an application
adding a listener of its own, overriding `extensions` to say so, dropping `SpringExtension` on the
way, and watching every spec fail on a missing bean. Extras go to the constructor and keep it.

Everything behind this — `stx-testing`, `spring-boot-starter-test`, Kotest — is `compile-only`, for
the reason the README's [Dependencies section](../libs/core/stx-spring-boot/README.md#dependencies) gives. Nothing in `src/` refers to `com.softistx.spring.testing` and no
auto-configuration imports it, so an application that never writes a spec never loads any of it.

