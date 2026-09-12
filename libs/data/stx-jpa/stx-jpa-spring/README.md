# stx-jpa-spring

One `stx-jpa` session factory for a Spring Boot application, behind one property.

**`io.github.softistx:stx-jpa-spring`** — [how to depend on it](../../../../docs/consuming.md).

```yaml
stx:
  jpa:
    enabled: true
    uri: postgresql://localhost:5432/orders
    packages: [com.acme.orders.domain]
```

```kotlin
class Orders(private val jpa: Jpa)   // injected like any other bean
```

Two beans, not one: `Jpa`, and the `Stage.SessionFactory` under it — for code written against
Hibernate Reactive's own type rather than this library's.

## `packages` is required, and a scan that finds nothing fails

Naming no packages would build a session factory that maps nothing, and the first query would then
fail with an unrelated message about an unknown entity. So the auto-configuration refuses at startup
naming `stx.jpa.packages`, and its wiring spec asserts that message.

That is the same rule `stx-jpa-ktor` applies to `packages(…)`: a list breaks the build when a class
moves, a scan finds nothing and starts perfectly, and the first query is where you would otherwise
learn about it.

## The schema is not this module's job

`SchemaMode` decides what Hibernate may do to a schema at startup, and the default says what
`stx-jpa`'s own README says: *a schema is migrated by something that keeps a history, not by an ORM
inferring one from the classes it happens to have been handed.* `stx-migrations-spring` is that
something. `SchemaMode.VALIDATE` is the cheap way to turn a mismatch into a startup failure once the
schema is managed elsewhere.

## Off unless asked for

`@ConditionalOnProperty(prefix = "stx.jpa", name = ["enabled"], havingValue = "true")` with **no**
`matchIfMissing`, and `@ConditionalOnMissingBean` on both beans.

Nothing connects at startup: Hibernate Reactive's pool opens its first connection when a session is
asked for, which is why the wiring spec needs no database — and why a wrong password surfaces on
first use.

## Why this is a module and not a package in `stx-spring-boot`

It used to be `com.softistx.spring.integration.jpa`, one of seven auto-configurations in the Spring
hub. Beside its own library it can be published and versioned on its own, and its `stx-jpa` edge
becomes `exported` rather than `compile-only`.

It depends on `stx-spring-boot` for nothing: an auto-configuration needs Spring Boot, not this
repo's Spring seam.

---

Apache-2.0 · [Contributing](../../../../CONTRIBUTING.md) · [All the libraries](../../../../README.md)
