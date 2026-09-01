# stx-mongo

Reusable MongoDB pieces for a Kotlin coroutine service: session-aware collection extensions covering
the create/read/update/delete shape every collection-backed service repeats, a cursor-paginated
`find`, the codecs the driver does not ship, and an audit trail a document can opt into.

They are extensions on `MongoCollection<T>` rather than a repository class to inherit from — see
[Writing a collection-backed service](#writing-a-collection-backed-service) for why.

It is a plain `jvm/lib` over `mongodb-driver-kotlin-coroutine`. Nothing here knows about a server
framework, so the same code serves a Ktor route, a kRPC service or a CLI.

## Getting a client

```kotlin
val client = mongoClient(uri)                         // settings, codec registry and all
val client = mongoClient(uri) { applyToSslSettings { … } }   // with a deployment's own opinions
val database = client.getDatabase("app")
```

That factory is the reason the plugin in `stx-ktor` is two lines
each: the knowledge that a client needs `mongoCodecRegistry()` is a fact about the driver, not about
a framework, so it lives here where a worker or a CLI can call it too.

The long form, for a client this factory does not cover — a reactive one for GridFS, say:

```kotlin
val settings = MongoClientSettings.builder()
    .applyConnectionString(ConnectionString(uri))
    .codecRegistry(mongoCodecRegistry())
    .build()

val reactive = MongoClients.create(settings)   // GridFS needs this one; skip it otherwise
val client = MongoClient(reactive)             // the coroutine API, over the same pool
val database = client.getDatabase("app")
```

The registry goes into the settings rather than onto the client, because `withCodecRegistry` answers
with a `MongoCluster` — everything you need to query, and no `close()`.

`mongoCodecRegistry()` is not optional decoration. It puts bson-kotlinx in front of the driver's
defaults, so a `@Serializable` data class maps by the annotations it already carries, and it carries
`InstantCodec`, without which the driver refuses a `kotlin.time.Instant` the moment one is passed as
a *value* — `Filters.gt("createdAt", now)`, an `Updates.set`, an audit stamp.

## Shape

```
com.strange.mongo            client, database and session helpers — collection(), withTransaction()
com.strange.mongo.codec      codecs the driver has no built-in for, and the registry that carries them
com.strange.mongo.query      what a collection is asked to do — filters, indexes, find/insert/update/delete
com.strange.mongo.page       PaginationOptions and the cursor-paginated find
                             (Page and PageInfo are stx-common's — stx-jpa answers with the same two)
com.strange.mongo.audit      AuditMetadata and Audited — who wrote a document, and when
com.strange.mongo.gridfs     a coroutine GridFS bucket over the Reactive Streams driver
```

Every extension that touches the database takes an optional `session: ClientSession?` in its last
position and runs inside it when one is passed, so a caller moves from no transaction to a
transaction by threading one value through instead of switching to a different API.

## Queries

`com.strange.mongo.query` is the collection surface: `findById`, `requireById`, `findOne`,
`findAll`, `findByIds`, `exists`, `existsById`, `existingIds`, `insert`, `insertAll`, `update`,
`updateById`, `updateAll`, `findAndUpdate`, `findByIdAndUpdate`, `delete`, `deleteById`,
`deleteByIds`, `deleteAll`, `findAndDelete`, `findByIdAndDelete`, plus `ensureIndex` /
`ensureUniqueIndex`.

**None of them is named after the driver method it wraps**, and that is not a style choice: a member
function wins over an extension with the same signature, so a `find(filter)` extension with a
defaulted `session` would never be called for the two-argument form. It would compile, read as if it
worked, and quietly run the driver's version — including the driver's defaults, which for
`findOneAndUpdate` means answering with the document as it was *before* the update. Different names
make that impossible.

Three of them do more than route a session:

- `existingIds(ids)` — the subset of `ids` that exists, in one query. The obvious loop over
  `existsById` is a round trip per id.
- `insertAll(documents)` — inserting nothing returns null instead of the driver's
  `IllegalArgumentException`, so a caller does not have to guard an empty list.
- `ensureIndex(key, options)` — creates the index unless an incompatible one already exists, in
  which case it returns null and leaves the existing one alone. Rebuilding an index on a live
  collection is a migration, not something a startup path should decide; every other error is still
  thrown.

## Pagination

`collection.findPage(options)` returns a `Page<T>` — the documents plus a Relay-shaped `PageInfo`
of `startCursor`, `endCursor`, `hasNextPage`, `hasPreviousPage`.

```kotlin
val page = notes.findPage(PaginationOptions(first = 20, sort = json("""{"tag": 1}""")))
val next = notes.findPage(PaginationOptions.first(20, page.info.endCursor))
```

It pages by **keyset**, not by `skip`. `skip(n)` makes the server walk and discard n documents, so
a page costs more the deeper it is and page 500 is a scan; resuming from the previous page's sort
key costs the same at any depth, and does not skip or repeat a document when one is inserted
between two requests.

What that requires, and what the implementation therefore does:

- **The sort key has to be unique**, so `_id` is appended to whatever `sort` asks for. Order by
  `name` alone and every document sharing a name is a coin toss between being served twice and
  being skipped — which is exactly the case `FindPageTest` walks end to end. `stx-jpa` *refuses*
  a sort that does not end in the identifier rather than appending one, and that is deliberate: its
  sort is Kotlin in the caller's own source, so it can name the line and what to add, while this
  one arrives as JSON from an HTTP client with nothing to point at.
- **The cursor carries every sort key**, base64url over extended JSON, so a date comes back as a
  date rather than as a string that compares against nothing. It also carries which keys it was
  issued for: a cursor from a differently sorted query is refused instead of paging along the wrong
  field.
- **Resuming with more than one key is an `$or`**, not a `$gt` — the first key is greater, *or* it
  is equal and the second is greater, and so on.
- **One extra document is fetched** beyond the page size. Whether it turned up is the whole answer
  to "is there another page", at the cost of one document rather than a second count query.

`filter` and `sort` are raw Mongo JSON because that is how they arrive from an HTTP client; both
are parsed, so a malformed one fails as `InvalidPaginationException` rather than reaching the
server.

## Writing a collection-backed service

There is no repository class and no CRUD service base class here, deliberately. Everything one of
those would have offered is an extension on `MongoCollection<T>` already, so a service holds the
collection and calls them:

```kotlin
class NoteService(
    private val notes: MongoCollection<Note>,
    private val principal: String? = null,
) {
    suspend fun create(input: NewNote): Note =
        notes.insertAndRead(Note(ObjectId().toHexString(), input.text))

    suspend fun update(id: String, input: EditNote): Note {
        val existing = notes.requireById(id)
        val changes = listOf(Updates.set("text", input.text)) + (existing as? Audited)?.updatedBy(principal).orEmpty()
        return notes.findByIdAndUpdate(id, Updates.combine(changes)) ?: throw DocumentNotFoundException("notes", id)
    }
}
```

Why this rather than a base class:

- **A repository over a collection was one-line delegation, twenty times over.** `findById`,
  `findAll`, `count`, `exists`, `deleteById` and the rest each forwarded to the extension of the same
  name. The one method that was not delegation, `insertAndRead`, is now an extension too.
- **An extension gets `T` from its receiver; a class cannot.** `MongoCollection<T>.insertAndRead`
  needs no `KClass`, no factory function and no constructor overloads, because `getCollection<T>` was
  the only thing that ever wanted a type token and it is the caller's call, made once, where the
  collection is resolved. Injecting `MongoCollection<Note>` is as ordinary as injecting a repository
  was, with nothing in between.
- **The hooks were the least reusable part.** `beforeCreate`/`afterCreate` and their four siblings
  existed so a subclass could get a word in edgewise; a service that simply writes its own `create`
  says the same thing in less, in the order it means, with no `super` call to remember.

What that leaves the library to provide is the two things a hand-written service should not have to
get right on its own:

- **`insertAndRead`** — insert, then read the document back as the collection now holds it, taking
  the id to read with from the driver's own `InsertOneResult`. A document whose `_id` the server
  assigned comes back exactly like one that carried its own.
- **`Audited.updatedBy(principal)`** — the operators that stamp an update, to combine with the ones
  the update is made of. Auditing stays opt-in by the entity: only a document implementing `Audited`
  gets a `metadata` object, which is why the `as?` above is the caller's to write. A null principal
  stamps nothing rather than writing an empty name over whoever really did touch it last. Creation
  metadata belongs where the document is built — `AuditMetadata.by(principal)`.

**Transactions stay explicit.** `withTransaction { }` on a `MongoCluster`, with the session passed to
each call that should join it. A single-document update is atomic in Mongo on its own, so a
transaction only begins to matter once a service writes twice — and at that point it should be
visible in the service, not configured into a base class.

## GridFS

The Kotlin coroutine driver has no GridFS — the API stops at collections — so `GridFsBucket` wraps
the Reactive Streams bucket, which is what the coroutine driver is built on anyway. Build the
reactive client first and hand it to both, and everything shares one connection pool:

```kotlin
val reactive = MongoClients.create(settings)
val client = MongoClient(reactive)                                   // the coroutine API
val files = GridFsBucket.of(reactive.getDatabase("app"), "uploads")  // the same pool

val id = files.upload("notes.txt", bytes, Document("contentType", "text/plain"))
val bytes = files.download(id)
```

It takes the *coroutine* `ClientSession`, so a caller inside `withTransaction` passes the same
session it passes everywhere else. Three things the API shape is deliberate about:

- **A download reads every chunk.** A GridFS download publishes one `ByteBuffer` per chunk — 255 KB
  by default — so awaiting the first one truncates anything larger to its first chunk and hands back
  a file of the right shape and the wrong length. `GridFsBucketTest` uploads 700 KB for exactly this.
- **A missing file is null, not an exception.** `download` checks the file exists and then streams
  it; the extra round trip buys the distinction that matters, since `MongoGridFSException` covers a
  missing file and a corrupt one alike and catching it would turn corruption into an empty 404.
- **A filename is not a key.** Uploading the same name twice keeps both files, which is what
  `download(filename, revision)` is for — `-1` is the newest, `0` the oldest.

One thing to know before writing into a transaction: the first upload into a bucket creates its
indexes, and an index cannot be created inside a transaction. Warm the bucket with one upload
outside first.
