# stx-amqp-spring

One `stx-amqp` connection for a Spring Boot application, behind one property.

```yaml
stx:
  amqp: { enabled: true, uri: amqp://localhost:5672, connection-name: orders-api }
```

```kotlin
class OrderEvents(private val amqp: Amqp)   // injected like any other bean
```

## What the bean owns

The connection, and nothing under it. A connection multiplexes and a channel does not, so channels,
publishers and consumers are opened by whoever needs one and closed by them — the same split
`stx-amqp-ktor` makes, for the same reason. The context closes the connection with itself through
the inferred `close()`, which is idempotent.

`connection-name` is worth setting: it is what the broker's management UI shows, and `orders-api`
beats an anonymous connection when something has to be traced back to a service.

## Off unless asked for

`@ConditionalOnProperty(prefix = "stx.amqp", name = ["enabled"], havingValue = "true")` with **no**
`matchIfMissing`, and `@ConditionalOnMissingBean` on the bean. Adding this module to a classpath
opens nothing; a deployment turns it on, and an application that declares its own `Amqp` wins
without turning anything off.

## Why this is a module and not a package in `stx-spring-boot`

It used to be `com.softistx.spring.integration.amqp`, one of seven auto-configurations in the Spring
hub. Beside its own library it can be published and versioned on its own, and its `stx-amqp` edge
becomes `exported` rather than `compile-only`.

It depends on `stx-spring-boot` for nothing: an auto-configuration needs Spring Boot, not this
repo's Spring seam.
