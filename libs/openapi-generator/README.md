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
                               interface files, writeAllTo, EmitException
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

Both emit `suspend` functions, group by OpenAPI tag by default (`categories-controller` →
`CategoriesApi`), and put models in a `<packageName>.model` sub-package so an interface and a schema
can share a name. `InterfaceNaming` decides the interface name's prefix and suffix — the defaults
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

`RealSpecTest` runs the parser over `apps/demo-api/openapi.yaml` — 49 declared operations across 8
tags, one of them `x-internal` — so a parser change that breaks on a real document fails here rather
than in a consuming module. It finds
the spec by walking up to the directory holding `project.yaml`, so it does not care what the working
directory is.
