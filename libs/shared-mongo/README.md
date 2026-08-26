# shared-mongo

Reusable MongoDB pieces for a Kotlin coroutine service: session-aware collection extensions, a
cursor-paginated `find`, the codecs the driver does not ship, and `MongoCrudService` — the
create/read/update/delete shape every collection-backed service repeats, with the parts that differ
left as hooks.

It is a plain `jvm/lib` over `mongodb-driver-kotlin-coroutine`. Nothing here knows about a server
framework, so the same code serves a Ktor route, a kRPC service or a CLI.

## Shape

```
com.strange.mongo            client, database and session helpers — collection(), withTransaction()
com.strange.mongo.codec      codecs the driver has no built-in for, and the registry that carries them
com.strange.mongo.query      what a collection is asked to do — filters, indexes, find/insert/update/delete
com.strange.mongo.page       Page, PageInfo, PaginationOptions and the cursor-paginated find
com.strange.mongo.crud       MongoCrudService and the audit stamp it applies
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
