# stx-redis-spring

One `stx-redis` connection for a Spring Boot application, behind one property.

**`io.github.softistx:stx-redis-spring`** — [how to depend on it](../../../docs/consuming.md).

```yaml
stx:
  redis: { enabled: true, uri: redis://localhost:6379, namespace: orders }
```

```kotlin
class CartStore(private val redis: Redis)   // injected like any other bean
```

## Off unless asked for

`@ConditionalOnProperty(prefix = "stx.redis", name = ["enabled"], havingValue = "true")` with **no**
`matchIfMissing`. Adding this module to a classpath opens no connection and starts nothing; a
deployment turns it on. That is the rule every `stx.*` auto-configuration in this repo follows, and
it is what makes an unused dependency free rather than merely cheap.

`@ConditionalOnMissingBean` on the bean, so an application that declares its own `Redis` wins
without having to turn this off.

## `stx.redis`, not `spring.data.redis`

Those configure different things. Boot's key configures Spring Data's template over Lettuce
directly; this one configures a `stx-redis` connection, which owns a namespace and a `Json`. An
application can have both, and they will not be the same connection.

## What is not a property

`RedisConfig.json` — because a `Json` is not a string, and a serializer configuration expressed in
YAML would be a second, worse way to write one. An application needing another declares its own
`Redis` bean and `@ConditionalOnMissingBean` steps aside.

`timeout` is a `java.time.Duration` (`10s`, `PT10S`) rather than a `kotlin.time.Duration`, because
Spring's binder has never heard of the latter — one written that way would bind only while nobody
set it, which is the worst kind of working.

## Why this is a module and not a package in `stx-spring-boot`

It used to be `com.softistx.spring.integration.redis`, one of seven auto-configurations in the
Spring hub. Beside its own library it can be published and versioned on its own, and its `stx-redis`
edge becomes `exported` rather than `compile-only` — in a module that *is* the Redis
auto-configuration, the library is not optional.

It depends on `stx-spring-boot` for nothing at all: an auto-configuration needs Spring Boot, not
this repo's Spring seam.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
