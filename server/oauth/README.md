# oauth

A Ktor GraphQL service for permissions and roles, and the only thing under `server/`. It is the
repository's **work in progress**, not one of its examples.

```bash
./kotlin run -m oauth          # /graphql, with the Apollo Sandbox at /sandbox
./kotlin test -m oauth
```

## What it is, and what it is not

The modules under `examples/` exist to prove a library: each one is the smallest thing that shows
a library end to end, and it is finished when it does.
This module is the other kind — an application being built, whose resolvers still answer with three
hardcoded permissions and whose Mongo collection is not read yet. Read it for how the pieces are
wired together, not as a reference for what a service should contain.

Like every example, it depends on the libraries as **published artifacts** rather than as modules —
it applies [`stx-artifacts.module-template.yaml`](../../stx-artifacts.module-template.yaml), so
`./kotlin build -m oauth` compiles against whatever is in `mavenLocal` and a stale artifact shows up
here as a compile error.

## The schema owns the types

The GraphQL SDL under `resources/graphql/` is the source of truth, and the
[`dgs-codegen`](../../plugins/dgs-codegen/README.md) plugin generates the Kotlin types from it at
build time into `com.softistx.oauth.graphql`. Resolvers are Koin singletons implementing
`GraphixResolver`, discovered by [`stx-graphix-koin`](../../libs/api/stx-graphix/stx-graphix-koin/README.md)
and served by [`stx-graphix-ktor`](../../libs/api/stx-graphix/stx-graphix-ktor/README.md):

```kotlin
@Singleton
class PermissionController : GraphixResolver {
    @QueryMapping suspend fun permissions(): List<Permission> = …
    @BatchMapping suspend fun metadata(permissions: List<Permission>): Map<Permission, AuditMetadata> = …
    @MutationMapping suspend fun createPermission(@Argument input: CreatePermissionInput): Permission = …
    @SubscriptionMapping fun permissionChanged(): Flow<PermissionEvent> = …
}
```

**On the SDL path, the Kotlin return type is read by nobody.** The document declares
`permissions: [Permission!]!` and the engine trusts it, so a resolver whose Kotlin signature
disagrees compiles cleanly and fails only when a client asks — which is exactly how this module once
shipped `Can't resolve value (/permissions) : type mismatch error, expected type LIST`.
`test/PermissionQueryTest.kt` is the answer to that: it builds `Graphix` directly, with no Ktor, no
Koin and no Mongo, and executes the query. Booting the three to ask a question about the schema
would test the wiring instead.

## Configuration

`resources/application.yaml`, read through Ktor's `ApplicationConfig` — including
`stx.mongo.uri` and `stx.mongo.database`, which `configureDatabase()` hands to
[`stx-mongo-ktor`](../../libs/data/stx-mongo/stx-mongo-ktor/README.md)'s `install(MongoDB)`.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
