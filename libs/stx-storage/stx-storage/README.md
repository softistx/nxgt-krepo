# stx-storage

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
com.softistx.storage         ObjectStorage — the client, its lifecycle, and the buckets on it
com.softistx.storage.bucket  StorageBucket — one bucket's objects: put, get, stat, list, copy, delete
com.softistx.storage.presign URLs and forms that carry their own authorisation, for browsers
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

## Presigned URLs

A presigned URL carries its own authorisation: whoever holds it can do exactly the one thing it was
signed for, until it expires, without ever seeing the credential. That is how an upload or a
download stops flowing through the application.

```kotlin
avatars.presignedGet("users/42.png")                            // 15 minutes by default
avatars.presignedGet("report.pdf", 1.hours, filename = "Q3.pdf")
avatars.presignedPut("incoming/${'$'}uploadId")
avatars.presignedDelete("users/42.png")
```

For a browser upload with terms attached, sign a form instead:

```kotlin
val form = avatars.presignedPost(
    key = "users/42.png",
    expiry = 10.minutes,
    sizeRange = 1L..5_000_000L,
    contentType = "image/png",
)
// form.url + form.fields go to the browser; it posts them, then the file part, last.
```

- **PUT signs the key and nothing else.** No size limit, no content-type limit — anyone holding the
  URL can send a gigabyte of anything to that key. `presignedPost` is the form that can say no:
  `sizeRange` and `contentType` become conditions inside the signed policy, and the store rejects an
  upload that breaks them.
- **Field order in the POST matters.** The signed fields go before the file part, so the store reads
  the policy on the way past and refuses at the first failed condition rather than after receiving
  the whole body.
- **Its URL is path-style** — the endpoint with the bucket as the first path segment, which is how
  MinIO addresses one. A deployment on virtual-host-style S3 (`bucket.s3.region.amazonaws.com`)
  needs that URL built from its own hostname.
- **Signing never touches the store's contents.** A URL for an object that is not there signs
  happily and answers 404 when it is used; the only round trip signing may make is the first
  region lookup, which is why these are `suspend`.
- **Seven days is the ceiling**, and one second the floor — SigV4's limits. Asking for more throws
  `InvalidExpiryException` here rather than producing a URL the store will reject.
