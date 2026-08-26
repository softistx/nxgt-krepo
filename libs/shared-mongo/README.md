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
