# openapi-generator

Reads an OpenAPI 3 document and emits Kotlin source: the spec's schemas as data classes, and
optionally a typed HTTP client over them. It has no dependency on the build system —
[`plugins/openapi`](../../plugins/openapi/README.md) is what wires it into a build, and this module
is a plain `jvm/lib` you can call from anywhere and test without compiling anything.

```kotlin
val model = OpenApiParser(Grouping.Tag, InterfaceNaming(suffix = "Api")).parse(Path("openapi.yaml"))
val files = KtorfitEmitter().emit(model, EmitOptions("com.strange.demo.client.api"))
files.forEach { it.writeTo(outputDir) }
```

**Which parts of a document are read, and what each becomes, is
[`docs/openapi-support.md`](../../docs/openapi-support.md)** — type mapping, inline schemas,
composition, enums, vendor extensions, name collisions, and what is not handled. This README is
about the module: its shape, what each client emitter produces, and how to add one.

## Shape

```
com.strange.openapi            the shared contract: the IR every other package speaks
com.strange.openapi.parser     reading a document — OpenApiParser, its options, OpenApiParseException
com.strange.openapi.emit       what every emitter needs: SourceEmitter, EmitOptions, type mapping,
                               interface files, the @ApiOperation seam and the exception hierarchy,
                               the error dispatch, writeAllTo, EmitException
com.strange.openapi.models     model emission — shared by every client style
com.strange.openapi.ktorfit    KtorfitEmitter — @GET/@POST interfaces
com.strange.openapi.spring     SpringEmitter  — @HttpExchange interfaces
```

The root package holds only the IR — `ApiModel`, `ApiGroup`, `Operation`, `Param`, `ModelType`,
`TypeRef`. Nothing there knows how a document is read or what a client looks like, which is what
keeps `parser` and the emitters from acquiring opinions about each other. Failure has the same
split: `OpenApiParseException` means the document is wrong, `EmitException` means the document is
fine but this client cannot express it, and no emitter imports from `parser` to say so.

The parser never knows which client it is feeding. It produces an `ApiModel` — groups of
`Operation`s and a list of `ModelType`s, with every type reduced to a `TypeRef` — and a
`SourceEmitter` turns that into KotlinPoet `FileSpec`s. Adding a client style means adding one
`SourceEmitter`; it does not mean touching the parser or the other emitters.

Model emission lives in `models/` rather than in either client, because both clients need exactly
the same classes. What differs is the *style*, and `ModelStyle` is the one place that difference is
written down:

| | `ModelStyle.Kotlinx` | `ModelStyle.Jackson` |
| --- | --- | --- |
| Class annotation | `@Serializable` | none needed |
| Renamed property | `@SerialName("created_at")` | `@JsonProperty("created_at")` |
| `date-time` | `kotlin.time.Instant` | `java.time.Instant` |
| Free-form object | `kotlinx.serialization.json.JsonObject` | `Map<String, Any?>` |
| `date` | `kotlinx.datetime.LocalDate` | `java.time.LocalDate` |
| `uuid` | `kotlin.uuid.Uuid` | `java.util.UUID` |
| Unknown fields | `@JsonIgnoreUnknownKeys` | `@JsonIgnoreProperties(ignoreUnknown = true)` |

A style governs the interfaces too, so a client's signatures always line up with its models.
`ModelsOnlyEmitter` is the `client: None` case — the same models, no API surface.

**One classpath consequence**: `format: date` in the kotlinx style emits `kotlinx.datetime.LocalDate`,
so a module whose document uses it needs `$libs.kotlinx.datetime`. Everything else the kotlinx style
emits is stdlib or kotlinx-serialization — `kotlin.uuid.Uuid` needs no opt-in on Kotlin 2.4, and
kotlinx-serialization binds it out of the box.

**Every generated model tolerates fields the document does not describe.** Both libraries are strict
by default — kotlinx always, Jackson whenever the consumer turns `FAIL_ON_UNKNOWN_PROPERTIES` on —
and neither switch belongs to this generator, so the tolerance is on the class. It is the same
reasoning as a tolerant enum: reading is where a client should bend.

A schema's `description` becomes KDoc on the generated class and its properties, and `deprecated:
true` becomes `@Deprecated`, so the document's own explanation reaches the IDE rather than stopping
at the YAML.

Parsing uses swagger-parser with `isResolve = true` but **not** `isResolveFully`: resolving fully
inlines every `$ref` and loses the component names, which are exactly what the generated model
classes are named after.

## What the two client emitters produce

| | `ktorfit` | `spring` |
| --- | --- | --- |
| Interface | `interface CategoriesApi` | same, annotated `@HttpExchange` |
| Operation | `@GET("categories/{id}")` | `@GetExchange(url = "categories/{id}")` |
| Path / query / header | `@Path` / `@Query` / `@Header` | `@PathVariable` / `@RequestParam` / `@RequestHeader` |
| Body | `@Body` + `@Headers("Content-Type: application/json")` | `@RequestBody` + `contentType` on the exchange |
| Multipart | `@Multipart` + `@Part` | `@RequestPart` + `contentType = "multipart/form-data"` |
| Optional multipart part | non-null (see below) | nullable |
| `HEAD` / `OPTIONS` / `TRACE` | `@HEAD` / `@OPTIONS` / not supported | `@HttpExchange(method = "…")` |
| Models | `ModelStyle.Kotlinx` | `ModelStyle.Jackson` |
| Failures | `install(ApiErrors)` | `.filter(apiErrorFilter(mapper))` |
| Credentials | `install(ApiAuth) { … }` | `.filter(apiAuthFilter(credentials))` |
| Operation → HTTP layer | `request.annotations` | `apiOperationProcessor()` + request attributes |

Both emit `suspend` functions, group by OpenAPI tag by default (`categories-controller` →
`CategoriesApi`), and write nothing to `packageName` itself — every file lands in one of three
sub-packages, by what it is rather than by what produced it:

```
<packageName>.apis      one interface per group
<packageName>.models    one declaration per schema
<packageName>.utils     the machinery a client needs and a caller mostly does not
```

That is what lets `Tag` the endpoint group and `Tag` the schema both exist, and it keeps the
surface a caller reads apart from the plumbing underneath it — `ApiProxySupport` is not an API. The
names are fixed: nothing about the layout depends on the consuming module, so a setting would only
be a second way to arrange the same files. `InterfaceNaming` decides the interface name's prefix and suffix — the defaults
are `""` and `"Api"`.

Two differences are not stylistic and will bite if they are "cleaned up":

- **A Ktorfit `@Part` may not be nullable.** ktorfit-ksp fails the build with `Part parameter type
  may not be nullable`, so an optional part is still emitted non-null. Spring has no such rule.
- **A Spring named parameter needs `required = false` when it is optional.** Spring's argument
  resolver throws on a null value for a required named parameter, so nullability alone is not
  enough.
- **Spring writes an enum argument with `Enum.name()`.** Its `ConversionService` never consults
  `toString()`, and the documented "the enum implements an interface with a converter" escape does
  not apply to a converter it has not been given — both checked against
  `DefaultFormattingConversionService` rather than assumed. So a generated enum used as a path,
  query or header parameter would go out as `IN_PROGRESS` where the document says `in-progress`: a
  request that succeeds and matches nothing. The Spring style therefore emits one extra file,
  `ApiEnumConverters.kt`, and the consumer hands it to the proxy factory:

  ```kotlin
  val conversions = DefaultFormattingConversionService().also(::registerApiEnumConverters)
  HttpServiceProxyFactory.builderFor(adapter).conversionService(conversions).build()
  ```

  Ktorfit needs none of this: it converts a parameter with `toString()`, which a generated enum
  overrides to return its wire value.
- **Spring has no annotation past the five common verbs.** `@GetExchange` and friends cover
  GET/POST/PUT/PATCH/DELETE; `HEAD`, `OPTIONS` and `TRACE` fall back to the generic
  `@HttpExchange(method = "HEAD")`. Anything else is an `EmitException` naming the method, because a
  proxy that quietly sent the wrong verb would be worse than a build failure.

## The one seam both clients share

Two things a client has to do — decide which error type a status maps to, and decide whether to
attach a credential — are per-operation facts, and both have to happen at the HTTP layer, where the
status code and the body live but the operation is anonymous. So every generated function carries
`@ApiOperation(id, security)`, added in `emit/ApiFile.kt` rather than by either emitter, and each
style reads it its own way:

- **Ktorfit** puts a function's own annotations on the request; a `createClientPlugin` reads them
  through `HttpRequest.annotations`. `ApiErrors` hooks `on(Send)`, which is the one hook that sees
  both the annotations and the response before anything reads the body as the success type.
- **Spring** cannot: by the time a `ClientRequest` exists the method is gone. The proxy factory does
  hand an `HttpRequestValues.Processor` the reflective `Method`, though, and `WebClientAdapter`
  copies the attributes it sets onto the request — so `ApiProxySupport.kt` carries the operation
  across as two request attributes and the filters read them back.

Both paths were checked against the running demo server before being written, not inferred from the
API surface — the same rule that produced `ApiEnumConverters.kt` in the phase before.

The annotation carries the operation's **id**, not the error mapping. An annotation could hold
`Array<KClass<*>>`, but turning a `KClass` back into a deserializer is reflection, and
kotlinx.serialization wants a `KSerializer` the generator can write down statically. So the
annotation carries a key and the mapping is generated code — a `when` over the operation and then
the status, in `emit/ApiErrorDispatch.kt`, shared by both styles and differing only in the
per-schema parse helper each one calls.

`ModelsOnlyEmitter` emits none of it. `client: None` means no API surface, and an exception nothing
can throw is API surface.

## Adding a client style

1. Add a package under `src/`, and a `SourceEmitter` in it.
2. Reuse what is already shared: `typeNameOf` and `apiFile` from `emit/`, and `modelFiles` from
   `models/`. Pick the `ModelStyle` your serializer needs; do not re-emit models.
3. Throw `EmitException` for anything the target cannot express, naming the operation. Do not
   silently drop it, and do not reach for `OpenApiParseException` — the document is not at fault.
4. Add a value to `ClientKind` in `plugins/openapi/src/OpenApiSettings.kt` and a branch to
   `ClientKind.emitter()` — those two lines are the only edit outside your own package.
5. Add a spec under `test/<package>/` driving `SAMPLE_MODEL` from `test/Fixtures.kt`, so the new
   emitter is compared against the same input as the others.

## Tests

```bash
./kotlin test -m openapi-generator
```

Specs are kotest `FeatureSpec`s grouped by scenario, per the repo convention in
[AGENTS.md](../../AGENTS.md).

`RealSpecTest` runs the parser over `examples/demo-api/openapi.yaml` — 52 declared operations across 10
tags, one of them `x-internal`, with 172 declared failures and a root `security` that 37 operations
inherit — so a parser change that breaks on a real document fails here rather
than in a consuming module. It finds
the spec by walking up to the directory holding `project.yaml`, so it does not care what the working
directory is.
