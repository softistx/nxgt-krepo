# shared-mongo

Reusable MongoDB pieces for a Kotlin coroutine service: session-aware collection extensions, a
cursor-paginated `find`, the codecs the driver does not ship, and `MongoCrudService` — the
create/read/update/delete shape every collection-backed service repeats, with the parts that differ
left as hooks.

It is a plain `jvm/lib` over `mongodb-driver-kotlin-coroutine`. Nothing here knows about a server
framework, so the same code serves a Ktor route, a kRPC service or a CLI.

## Getting a client

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
com.strange.mongo.page       Page, PageInfo, PaginationOptions and the cursor-paginated find
com.strange.mongo.repository MongoCrudRepository — one collection, as an object
com.strange.mongo.service    MongoCrudService — the write flow over a repository
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
  being skipped — which is exactly the case `FindPageTest` walks end to end.
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

## Repository

`MongoCrudRepository<T, ID>` is the query vocabulary as a noun: the thing a service holds, a test
substitutes, and a subclass extends with the two or three queries that really are specific to a
collection.

```kotlin
val notes = MongoCrudRepository(database.collection<Note>("notes"), Note::id)

class NoteRepository(database: MongoDatabase) :
    MongoCrudRepository<Note, String>(database.collection("notes"), Note::id) {
    suspend fun findByTag(tag: String) = findAll(Filters.eq("tag", tag))
    override suspend fun ensureIndexes() { collection.ensureIndex(Indexes.ascending("tag")) }
}
```

Two decisions worth stating:

- **`idOf` is a constructor parameter, not an abstract method**, so the plain case needs no subclass
  at all. Every method is `open` for the case that does.
- **The entity owns its `_id`.** Writes take the id from the document rather than from a
  server-generated one, which is what lets a create be "insert, then read back" without a round trip
  to discover what was inserted.

It knows nothing about *why* a document is being written — no hooks, no audit, no transactions.
That is `MongoCrudService`, one layer up.

## Service

`MongoCrudService<T, ID, C, U>` is the write flow every collection-backed service repeats — read it,
check it exists, write it, read it back, stamp who did it — with the two parts that are genuinely
about this collection left abstract:

```kotlin
class NoteService(repository: NoteRepository, principal: String?) :
    MongoCrudService<Note, String, NewNote, EditNote>(repository, principal) {
    override suspend fun buildCreate(input: NewNote) = Note(ObjectId().toHexString(), input.text)
    override suspend fun buildUpdate(existing: Note, input: EditNote) = listOf(Updates.set("text", input.text))
}
```

`beforeCreate`, `afterCreate`, `beforeUpdate`, `afterUpdate`, `beforeDelete` and `afterDelete` are
the seams for everything else; each of the delete and after hooks receives the session it is running
in, so a cascade lands in the same transaction as the delete that triggered it.

**Transactions are opt-in.** Pass a `MongoCluster` and every write that is not already in a session
opens one; leave it out and writes run as they come. That is a real choice, not a default: a
single-document update is atomic in Mongo on its own, so the transaction only begins to matter once
a hook writes something else. `MongoCrudServiceTest` has the pair — the same failing hook, rolled
back with a cluster and standing without one.

**Auditing is opt-in by the entity.** An update is stamped only when the document implements
`Audited` and a principal is known, so a collection that never asked for a `metadata` object does
not quietly grow one. Creation-time metadata belongs in `buildCreate`, where the entity is being
built anyway — `AuditMetadata.by(principal)`. An update that changes nothing writes nothing and is
not stamped either: the audit trail is for changes, not for requests.

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
