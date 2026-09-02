# stx-storage-ktor

`install(Storage)` — one S3-compatible object-storage client for the application, closed when it
stops.

```kotlin
install(Storage) { config = StorageConfig(endpoint, accessKey, secretKey) }

get("/avatar/{key}") { call.respondText(call.storage.bucket("avatars").presignedGet(key)) }
```

## The one plugin whose config is required

`RedisConnection` defaults its `config`, `KafkaCluster` defaults its `config`, and this one refuses
to. Two of `StorageConfig`'s three fields are **credentials**, and a credential with a default is a
credential in source control. So `install(Storage)` with no `config` and no `instance` fails at
startup with a message saying which, rather than quietly reaching for something.

## What it owns

The client, and nothing else. Buckets and objects are asked of the client per call; a presigned URL
is computed locally and reaches no network at all. `call.storage` is shared and safe to be — the
MinIO SDK's client is a pool.

## `injectable = true`

Registers the client the plugin already opened rather than letting the container open a second. The
container closes it as well at application stop, which is harmless because these clients close
idempotently. A client that has to outlive the application belongs in `instance`, which this plugin
adopts and does not close.

## Why this is a module and not a package in `stx-ktor`

It used to be `com.softistx.ktor.storage`, one of seven integrations in one artifact. Publishing it
beside its library is what the other families here already do, and it lets the `stx-storage`
dependency be `exported` instead of `compile-only`.

`own`, `publish`, `resource` and `required` stay in `stx-ktor`: they belong to no integration.
