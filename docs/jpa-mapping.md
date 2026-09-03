# What a stx-jpa entity may say

The mapping reference: which database, what a column ends up called, how an identifier is generated,
the two Kotlin types this module converts, JSON columns, and Bean Validation. This is the half of
`libs/stx-jpa` that gains an entry every phase — a `SqlTypes` code, a strategy, a converter — so
it lives here rather than in the module README, which answers *why the library is shaped this way*
and stays roughly the size it is.

`docs/jpa-criteria.md` is the other half — what a *query* may say. `libs/stx-jpa/stx-jpa/README.md` has
the reasoning behind both.

## Associations are lazy

Annotate every one of them — `@ManyToOne(fetch = FetchType.LAZY)`, `@OneToOne(fetch = LAZY)` — and
leave `@OneToMany`/`@ManyToMany` at their lazy default. **`@ElementCollection` is one of these**, which
is easy to miss because a `Set<String>` looks like a column: it is a plural attribute, it gets a table
of its own, it is lazy, and reading it unfetched throws exactly like an association.
`ValueMappingTest` measures all three, and `RelationshipTest` measures that `@OneToOne` and
`@ManyToMany` behave the same way — there is no association shape here that is exempt. JPA's default for the to-ones is `EAGER`,
which is a select per distinct owner behind any query returning more than one row.

What makes this a rule rather than advice is that the reactive session has no transparent lazy
loading, so the two options are not "fast" and "slow": an unfetched lazy association throws, and an
eager one silently multiplies statements. Neither is something to discover in production. The query
says what it loads — `fetch`, `fetchEach`, or a projection that loads no entity — and
`libs/stx-jpa/stx-jpa/README.md` has the reasoning under *A query says what it loads*.

## The foreign key as a column

The rule above assumes the query knows what will be read. Sometimes it cannot — a GraphQL request
loads the parents before anything knows whether the child field was selected — and then neither
answer works: fetching always loads what nobody asked for, and fetching never leaves the caller
holding an association it may not touch. Map the key a second time, as a plain column:

```kotlin
@Entity
class Purchase(
    @Id var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY) var customer: Buyer? = null,
) {
    @Column(insertable = false, updatable = false)
    var customerId: Long? = null
}
```

**Naming is orthogonal.** The two flags say how the column may be *written*, not what it is called,
so `Naming.SNAKE_CASE` still derives `customer_id` from `customerId` and the association's own join
column lands on the same name — which is why the two mappings meet without either of them being
told. Spell a `name` only where you would have spelled one anyway.

**The two flags are not decoration.** Without them Hibernate refuses to build the factory at all,
and names the remedy itself:

```
Column 'customer_id' is duplicated in mapping for entity '…Purchase'
(use '@Column(insertable=false, updatable=false)' when mapping multiple properties to the same column)
```

That is what makes this a second *view* of one column rather than a second column. `customer` stays
the writable side and remains the only way to change what the row points at.

**It costs nothing.** The value is already in the row that loaded the owner, so reading it goes back
for nothing — measured as zero `entityFetchCount` in `ForeignKeyColumnTest`, beside the reading of
`customer` that throws in the same scenario.

**And it goes stale.** A read-only mapping is never written back, so an instance whose association
has just been reassigned still reports the *old* key, flush included:

```kotlin
val purchase = session.get<Purchase>(1L)
purchase.customer = session.get<Buyer>(2L)
session.flush()

purchase.customerId    // still 1 — correct only after the owner is loaded again
```

Nothing warns about this and nothing can. Treat the scalar as what the row said when it was read: a
key to look something up with, never a value to decide a write from.

The reason it is worth the trouble is `@BatchMapping` in
[`docs/graphix.md`](graphix.md) — a DataLoader has to key on something, and reading the association
to learn its id is precisely the throw the batch was avoiding.

## Which database

Postgres, MySQL and DB2. Hibernate Reactive names none of them: it picks a driver at runtime from
the URI scheme, so the only thing that changes is `postgresql://`, `mysql://` or `db2://` — the
entity, the session, the transaction and the HQL are the same, which is what `MySqlTest` exists to
show rather than assert in prose.

All three drivers are declared `runtime-only`. They reach an application's runtime classpath and are
kept off its compile classpath, which is right twice over: nothing in this library references a
driver class, and a `PgBuilder` in application code is a second connection pool nobody is managing.
Two unused drivers cost about a megabyte and load no class. `DriversTest` asserts all three are
actually there, since a dependency scope is a claim about a classpath that nothing else would notice
being wrong.

**MySQL 8.4 needs one thing said out loud.** Every account it creates uses `caching_sha2_password`,
whose first authentication requires either TLS the client trusts or the server's RSA public key.
A reactive client given neither drops the connection, and the error —
`ClosedConnectionException: Failed to read any response from the server` — reads like a network
fault. It is an authentication one. `mysqlContainer()` in `stx-testing` moves its `root` account
onto `mysql_native_password` for exactly this reason, and says so at the point it does it.

DB2 is shipped and unproven here: the driver is on the classpath and `DriversTest` covers that, but
no spec has run against a DB2 server, because `icr.io/db2_community/db2` wants a privileged container
and several gigabytes and this repo does not put a machine under that unasked. Point `DB2_TEST_URI`
at one and the same specs are what should run — except the JSON ones, which cannot: `DB2Dialect`
registers no DDL type for `SqlTypes.JSON` at all.

## Column names

`createdBy` is the column `created_by`.

**That is this library's doing, not Hibernate's.** Hibernate keeps the property name and Postgres
folds the unquoted identifier, so on its own it gives you `createdby` — it is Spring that installs a
snake-case strategy, and the two are met together often enough that almost everyone believes
otherwise. A column is read by psql, by a migration and by whoever is looking at the database without
this application in front of them, so it is written the way SQL is written.

**A name you write is used exactly as you wrote it.**

```kotlin
@Entity
class Order(
    @Id var id: Long = 0,
    var createdBy: String = "",                             // created_by
    @Column(name = "lastSeen") var lastSeen: String = "",   // lastSeen, folded by Postgres to lastseen
)
```

This is an `ImplicitNamingStrategy`, which runs where Hibernate is deciding a name it was not given —
not the `PhysicalNamingStrategy` that Spring and Hibernate's own `PhysicalNamingStrategySnakeCaseImpl`
use, which rewrites every identifier including the ones an entity spells out and leaves quoting as the
only way out. For an entity that names nothing the two are identical; they differ only where somebody
said what they wanted. It is also what makes mapping an existing camelCase schema possible without
quoting every identifier in it.

The splitting rule is Hibernate's own, copied so that switching strategies later renames nothing: an
underscore goes where a lower-case letter or digit is followed by an upper-case letter followed by a
lower-case letter or digit. So an acronym stays glued — `orderURL` is `orderurl`. `NamingTest` asserts
that equivalence against Hibernate's class rather than describing it.

`JpaConfig(naming = Naming.AS_WRITTEN)` turns it off, for a schema that already exists and was not
built this way. **Changing this setting renames every column that was not named by hand**, so it is a
decision to make before there is a schema rather than after.

## Identifiers

`@GeneratedValue` works as it does anywhere: `AUTO` and `SEQUENCE` both use a sequence on Postgres,
`IDENTITY` works, and `UUID` works **on a `java.util.UUID`**. `GeneratedIdTest` runs all four against
a real server, because Hibernate Reactive is where a generator that needs a round trip has to be a
`ReactiveIdentifierGenerator`, and a strategy that does not work is a bootstrap error rather than
something a mock would show.

**A `kotlin.uuid.Uuid` cannot be the identifier, and `Jpa.connect` refuses one.** Hibernate rejects
an `AttributeConverter` on an `@Id` outright, and the JDBC-bound `UserType` that would otherwise map
it is what Hibernate Reactive's own documentation says not to reach for. Without a converter nothing
fails: the type is serialized, the primary key comes out `bytea`, inserts and reads both work, and
the table is unreadable to every other client of the database. So the check is at `connect`, which is
the last moment it is still preventable. Use `java.util.UUID` for the key; `kotlin.uuid.Uuid` is fine
on every other attribute.

## Instant and Uuid

`kotlin.time.Instant` and `kotlin.uuid.Uuid` are not JPA basic types, and an unmapped type is not
refused — it is serialized. Everything succeeds, and the column holds bytes no other client of that
database can read, compare or index. Two `autoApply` converters are registered with every factory,
so an entity writes the Kotlin types with nothing on the property and gets `timestamp with time zone`
and `uuid`. Each is pinned by a scenario that asks `information_schema` what the column actually is;
a round trip cannot show this, because a mapping that writes a blob reads that blob back and agrees
with itself.

An attribute that wants something else opts out with `@Convert(disableConversion = true)`. An
application's own converters are named in `Jpa.connect(config, entities, converters)`, because
`addAnnotatedClass` finds no `@Converter` — or found for it by `Jpa.scan`, which is the one job the
scan does that naming the classes cannot.

## JSON columns

A structured value in one column, two ways. **Reach for the first one.**

```kotlin
@Embeddable
class Coordinates(var latitude: Double = 0.0, var longitude: Double = 0.0)

@Entity
class Place(
    @Id var id: Long = 0,
    @Embedded @JdbcTypeCode(SqlTypes.JSON) var at: Coordinates = Coordinates(),
)

jpa.session { it.query<Long>("select p.id from Place p where p.at.latitude > :south").parameter("south", 50.0).single() }
```

An `@Embeddable` needs no `@Serializable` and nothing from this module: Hibernate builds the document
from its own mapping model. That is worth having for what it buys — **HQL paths into it**, checked
against the mapping at startup, and every field keeping its own mapping, so the `Instant` and `Uuid`
converters apply *inside* the document. A shape you know is a shape the database should know.

The second way is for a shape you deliberately do not map — an open-ended payload, a versioned
document, a sealed hierarchy:

```kotlin
@Serializable
data class Address(val street: String, val city: String, val country: String = "DE")

@Entity
class Customer(
    @Id var id: Long = 0,
    @JdbcTypeCode(SqlTypes.JSON) var address: Address = Address("", ""),
    @JdbcTypeCode(SqlTypes.JSON) var tags: Map<String, String> = emptyMap(),
    @JdbcTypeCode(SqlTypes.JSON_ARRAY) var labels: List<String> = emptyList(),
)
```

**This is the part that does not work without this library.** Hibernate resolves the JSON mapper by
looking for Jackson, then Jackson 3, then JSON-B, and none of the three is a dependency here — so
without a mapper of ours the annotation above compiles, exports a `jsonb` column, and throws on the
first write telling a Kotlin codebase to install Jackson. `Jpa.connect` registers a `FormatMapper`
over kotlinx.serialization instead, so `@Serializable` is what makes a JSON column work. Generic
attributes resolve from the reflective type, so `Map<String, String>` keeps its type arguments.

**Two codes, and the wrong one used to be a runtime accident.** `SqlTypes.JSON` is for a document that
is an object, `SqlTypes.JSON_ARRAY` for one that is a list. Both make a `jsonb` column; given the
wrong one the reactive binder wraps the document in the wrong Vert.x type and every write fails with
`DecodeException: Failed to decode` and nothing else. `Jpa.connect` refuses the mismatch at startup
instead, naming the attribute and the code to use.

**What the stored document looks like, and why.** `jpaJson` is `lenientJson` with
`encodeDefaults = true`. kotlinx otherwise omits a property that equals its default, and a `jsonb`
column is read by SQL as well as by the class that wrote it — `address->>'country'` would be null for
exactly the rows whose country happened to be the default, and a functional index over it would miss
them. Nulls stay explicit for the same reason: `jsonb_exists(address, 'note')` and `is null` are
different questions, and only a document that writes the key can answer both. Unknown keys are
ignored on the way in, so a document written by an older version of a class still reads. Override the
whole thing with `JpaConfig(json = …)`.

**Editing a document is not free.** Hibernate's dirty check for a JSON attribute is
`fromString(toString(value))` — a real round trip through the mapper on every check — so an in-place
mutation *is* noticed, at the cost of serializing the document to find out. Keep such a column small,
and prefer a mapped column for anything you filter or sort on.

**Or a converter instead of the mapper.** An `AttributeConverter<T, String>` doing the serialization
itself bypasses the `FormatMapper` entirely, and `@Convert` composes with `@JdbcTypeCode(SqlTypes.JSON)`
— the column is still `jsonb` and the converter is what wrote it, which `ConvertedJsonTest` measures
both ways. Without the JSON code it is a `character varying` column instead, which costs the operators
and the indexes.

It is a real alternative, with a real trade. What it buys is control — a per-attribute `Json`, a
custom shape, a migration between two document versions — and a dirty check on the domain value
rather than the `fromString(toString(value))` round trip below. What it costs is that the converter
is named on each attribute instead of applying to every `@Serializable` type at once, and that HQL
still cannot see inside the document either way. **It is not a way to drop this module's mapper**:
Hibernate has no JSON formatter of its own to fall back to — it looks for Jackson, then Jackson 3,
then JSON-B, and throws when it finds none — so removing the kotlinx one means adding Jackson or
converting every JSON attribute by hand.

**Not on DB2.** `DB2Dialect` registers no DDL type for `SqlTypes.JSON`, so schema export fails with
*No type mapping for org.hibernate.type.SqlTypes code: 3001 (JSON)*. Postgres gives `jsonb`, MySQL
`json`, and both are pinned by a scenario asking `information_schema` what the column really is.

Hibernate 7.4 also has HQL `json_value`, `json_query` and `json_exists`, disabled by default behind
`hibernate.query.hql.json_functions_enabled` while they incubate. This library does not enable them
and no spec here has run one — set it through `JpaConfig.properties` if you want them, or query a
document through `nativeQuery` and the database's own operators.

## Inheritance

`SINGLE_TABLE` and `JOINED` both work, and a query on the base type is polymorphic — each row comes
back as its own subclass. `InheritanceTest` pins both, including that the discriminator is a real
column and that a `JOINED` subclass gets its own table holding only what it added.

The Kotlin side is not free, and it is already paid for: an `@Entity` subclass needs its superclass
open and every class needs a no-arg constructor, which the `allOpen` and `noArg` compiler plugins
this module configures supply. Nothing extra is required of an entity author.

## Soft delete

**Use `@SoftDelete`.** Hibernate generates the statements, so the rewritten delete and the restriction
on every read are its own SQL — schema-qualified, in the driver's own placeholder dialect, and
portable across the databases here. `SoftDeleteTest` measures both halves: the entity is gone from
every read, and the row is still in the table when counted with SQL that does not go through the
mapping.

**`@SQLDelete` and `@SQLRestriction` do not survive the trip**, and the two reasons compound:

- the statement is handed to the driver **untouched**, so a JDBC `?` reaches the server literally —
  there is no JDBC under the Vert.x pool to read it — and Postgres answers `syntax error at end of
  input`;
- correcting it to `$1` uncovers the second: the table name is not schema-qualified, and nothing can
  qualify it, because `JpaConfig.schema` is a runtime value and an annotation is a compile-time
  constant. The README records the same trap for `nativeMutate`.

Both failures are pinned by specs rather than described, because the first one looks like the whole
problem.

## Cascade and orphan removal

Both work, and `RelationshipTest` pins the two that matter: a child dropped from a collection with
`orphanRemoval = true` is **deleted** rather than merely detached, and removing an owner removes what
it owned. `cascade = [CascadeType.ALL]` on a `@OneToOne` persists the held entity with its owner.

The reason it is measured rather than assumed is that cascade is the persistence context doing work
at flush, and a reactive session is a different persistence context. Note the collection has to be
loaded for either to happen — `fetchEach` it first, or Hibernate has nothing to compare against.

## Composite keys

`@EmbeddedId` works, and the identifier reaches `find`/`get` intact even though their parameter is
`Any`. Its parts are a path in HQL — `where key.stream = :stream` — which is the reason to embed one
rather than reach for `@IdClass`. Give the embeddable `equals` and `hashCode`; the persistence
context needs them and Kotlin will not write them for a non-`data` class.

`@Formula` is the read-only twin: a SQL fragment spliced into every select, computed by the database,
never written, and — as `CompositeKeyTest` confirms against `information_schema` — not a column at
all.

## Optimistic locking

`@Version` works, and the conflict surfaces where it should: a write built on a version the row has
left behind is refused at flush, inside the session, as a thrown exception — not as a failed future
a suspending call quietly drops. `OptimisticLockTest` pins the refusal and that the winner's value is
the one that survived.

## Validation

Hibernate Validator is on the classpath and exported, so constraints on an entity are checked before
it is written — no configuration, no explicit `Validator`, nothing to call:

```kotlin
@Entity
class Order(
    @Id @GeneratedValue var id: Long = 0,
    @field:NotNull @field:Size(min = 2, max = 64) var reference: String? = null,
)
```

Note `@field:`. A Kotlin constructor property is a parameter, a property and a field at once, and a
constraint annotation that lands on the parameter is one Hibernate never sees.

Whether this works at all was worth asking rather than assuming: Hibernate ORM applies constraints
through event listeners, and Hibernate Reactive replaces the listeners it fires. It keeps them —
`ValidationTest` persists a violating entity and gets a `ConstraintViolationException` with nothing
written, and the alternative would have been a library that silently stores whatever it is handed.

**The constraints reach the schema too.** `@Size(max = 64)` exports as `varchar(64)` rather than the
default 255, which the same spec asserts against `information_schema` — so a constraint is one
statement of a rule rather than two that can drift apart.

`expressly` comes along as a runtime-only dependency. Hibernate Validator interpolates a message like
*"must be between {min} and {max}"* through Jakarta Expression Language and ships no implementation
of one; without it the first constraint is a `NoClassDefFoundError`.
