# shared-storage

S3-compatible object storage for a Kotlin coroutine service, over the
[MinIO Java SDK](https://min.io/docs/minio/linux/developers/java/API.html).

The SDK is half asynchronous and half not: `putObject` and friends answer with a `CompletableFuture`,
while `listObjects` and `removeObjects` hand back an `Iterable` that does network I/O as you walk it,
and a `getObject` response is an `InputStream` that blocks on read. Called from a coroutine, the
second half stalls whichever thread it lands on — and on `Dispatchers.Default` that is a thread the
rest of the application needed. Every operation here is suspending or a `Flow`, and the blocking half
is the reason this module exists rather than being a thin renaming of the SDK.

It is a plain `jvm/lib`. Nothing here knows about a server framework, so the same code serves a Ktor
route, a background worker or a CLI.

## Shape

```
com.strange.storage         ObjectStorage — the client, its lifecycle, and the buckets on it
com.strange.storage.bucket  StorageBucket — one bucket's objects: put, get, stat, list, copy, delete
```
