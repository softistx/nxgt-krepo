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

## Getting a client

```kotlin
val storage = ObjectStorage.connect(
    StorageConfig(
        endpoint = "http://localhost:9000",
        accessKey = System.getenv("S3_ACCESS_KEY"),
        secretKey = System.getenv("S3_SECRET_KEY"),
    ),
)

val avatars = storage.ensureBucket("avatars")   // creates it if it is not there
```

`StorageConfig` has no default credentials, on purpose: a default is a credential in source
control. `ensureBucket` swallows only `BucketAlreadyOwnedByYou`, so two instances starting at once
both get a bucket and neither gets an exception. `ObjectStorage` is `AutoCloseable` and one instance
is meant to be shared — the SDK pools connections behind it.

## Objects

```kotlin
avatars.put("users/42.png", bytes, contentType = "image/png")
avatars.put("backup.tar", stream, size = -1)          // -1: length unknown, uploaded in parts

val bytes = avatars.get("users/42.png")               // null if absent
val same  = avatars.require("users/42.png")           // ObjectNotFoundException if absent

avatars.stat("users/42.png")?.size
avatars.exists("users/42.png")

avatars.list(prefix = "users/").collect { println(it.key) }

avatars.copy("users/42.png", "users/42.backup.png")   // server-side, the bytes never come here
avatars.delete("users/42.png")
avatars.deleteAll(listOf("a", "b"))                   // returns the keys the store refused
```

A few things are worth knowing before reading the source:

- **Absent is `null`, not an exception.** `get` and `stat` translate the SDK's `NoSuchKey` error into
  `null`; `require` is the variant that throws, and it names the bucket and key. `delete` on a key
  that is not there is a success, because S3 says it is.
- **`read` is the streaming form.** `get` loads the whole object into a `ByteArray`; `read` hands the
  `InputStream` to a block on `Dispatchers.IO` and closes it afterwards, and takes an `offset` and
  `length` for a ranged request. Reach for it when the object may be large.
- **`list` is a `Flow`, and the paging is the SDK's.** Walking the SDK's `Iterable` fetches the next
  page, which is blocking I/O, so the flow is `flowOn(Dispatchers.IO)`. `recursive = false` asks the
  store to collapse each level into a single `prefix/` entry — S3 has no directories, only that
  illusion offered on request.
- **`deleteAll` walks its own result.** The SDK's `removeObjects` deletes nothing until the returned
  `Iterable` is consumed; a caller who ignores the return value deletes nothing and is told nothing.
  This one consumes it and returns the keys the store refused, so an empty list means everything went.
