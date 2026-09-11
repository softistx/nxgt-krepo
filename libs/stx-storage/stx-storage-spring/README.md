# stx-storage-spring

One `stx-storage` client for a Spring Boot application, behind one property.

**`io.github.softistx:stx-storage-spring`** — [how to depend on it](../../../docs/consuming.md).

```yaml
stx:
  storage: { enabled: true, endpoint: http://localhost:9000, access-key: …, secret-key: … }
```

```kotlin
class Avatars(private val storage: ObjectStorage)   // injected like any other bean
```

## No credential has a default

`accessKey` and `secretKey` are nullable with no fallback, and the auto-configuration fails naming
the missing key rather than reaching for something. **A credential with a default is a credential in
source control**, and a default that happens to be right on one machine is worse than one that is
wrong everywhere — it works until it is deployed.

Its wiring spec asserts both halves: that the failure names `stx.storage.access-key`, and that the
properties class really does default them to null. `stx-storage-ktor` makes the same choice on the
Ktor side, where `config` is required while its siblings' are defaulted.

## Off unless asked for

`@ConditionalOnProperty(prefix = "stx.storage", name = ["enabled"], havingValue = "true")` with
**no** `matchIfMissing`, and `@ConditionalOnMissingBean` on the bean.

## The spec points at a port nothing serves

`http://127.0.0.1:1`, not `localhost:9000`. The MinIO client is lazy about its first request, so the
wiring can be asserted without a store — but the properties used to name the workspace's own MinIO,
and a wiring spec pointing at a port somebody else is serving is one library change away from doing
something on it.

## Why this is a module and not a package in `stx-spring-boot`

It used to be `com.softistx.spring.integration.storage`, one of seven auto-configurations in the
Spring hub. Beside its own library it can be published and versioned on its own, and its
`stx-storage` edge becomes `exported` rather than `compile-only`.

It depends on `stx-spring-boot` for nothing: an auto-configuration needs Spring Boot, not this
repo's Spring seam.

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
