# stx-kafka-spring

One `stx-kafka` cluster handle for a Spring Boot application, behind one property.

```yaml
stx:
  kafka: { enabled: true, bootstrap: localhost:9092, client-id: orders }
```

```kotlin
class OrderStream(private val kafka: Kafka)   // injected like any other bean
```

## The bean holds no connection

This is the one integration here whose context is **not** holding an open resource, and it is not an
omission. A Kafka client connects when it is constructed, and a producer, a consumer and an admin
client have different lifetimes, threads and failure modes — so the connections belong to the
publishers and subscribers the cluster hands out, each closed by whoever asked for one.

That is also why its wiring spec needs no broker: there is nothing to connect. `stx-kafka-ktor`'s
README makes the same argument from the other side.

## Off unless asked for

`@ConditionalOnProperty(prefix = "stx.kafka", name = ["enabled"], havingValue = "true")` with **no**
`matchIfMissing`, and `@ConditionalOnMissingBean` on the bean. Adding this module to a classpath
registers nothing; a deployment turns it on, and an application that declares its own `Kafka` wins
without turning anything off.

## Why this is a module and not a package in `stx-spring-boot`

It used to be `com.softistx.spring.integration.kafka`, one of seven auto-configurations in the
Spring hub. Beside its own library it can be published and versioned on its own, and its
`stx-kafka` edge becomes `exported` rather than `compile-only`.

It depends on `stx-spring-boot` for nothing: an auto-configuration needs Spring Boot, not this
repo's Spring seam.
