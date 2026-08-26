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

A style governs the interfaces too, so a client's signatures always line up with its models.
`ModelsOnlyEmitter` is the `client: None` case — the same models, no API surface.

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
- **Spring has no annotation past the five common verbs.** `@GetExchange` and friends cover
  GET/POST/PUT/PATCH/DELETE; `HEAD`, `OPTIONS` and `TRACE` fall back to the generic
  `@HttpExchange(method = "HEAD")`. Anything else is an `EmitException` naming the method, because a
  proxy that quietly sent the wrong verb would be worse than a build failure.

## Type mapping

| OpenAPI | Kotlin |
| --- | --- |
| `string` | `String` |
| `string` / `date-time` | `Instant` (which one follows the `ModelStyle`) |
| `string` / `binary` | `ByteArray` |
| `integer`, `integer` / `int64` | `Int`, `Long` |
| `number` | `Double` |
| `boolean` | `Boolean` |
| `array` | `List<T>` |
| object with properties | a generated model class |
| object without properties | the free-form type |
| no 2xx response schema | `Unit` |

A `$ref` to a schema with no properties resolves to the underlying type rather than to a class name
— `Upload: {type: string, format: binary}` is a `ByteArray`, not a `class Upload` that nothing would
ever emit.

Optional properties and parameters are nullable and default to `null`, and parameters are ordered so
that everything with a default comes last.

## Colliding names

Two different things in a spec can want the same Kotlin name, and the generator's rule is that a
collision is either merged or fatal — never silently resolved by whichever one is written last.

- **Tags that normalise to one interface name are merged.** `categories-controller` and
  `categories` both mean `CategoriesApi`, so their operations end up in one interface rather than in
  two files racing to the same path.
- **Two operations that would declare the same function name fail**, and the message names both as
  `METHOD /path` so the offending pair is findable in the document.
- **Two schemas that would declare the same class fail**, naming the class. `page_info` and
  `pageInfo` are both `PageInfo`.
- **`writeAllTo` refuses a batch containing two files with the same fully-qualified name**, which
  catches anything the earlier checks did not — writing them in sequence would let the last win.

Where a spec is merely *ambiguous* rather than contradictory, the choice is pinned down instead of
left to document order: an operation with several 2xx responses takes its return type from the
**lowest** one, so `200` wins over `201` however the YAML lists them.

## What it does not handle

`allOf` / `oneOf` / `anyOf`, enums, and `additionalProperties` are not modelled; a schema using them
falls back to the free-form type or to its first resolvable shape. An unsupported request media type
is an error naming the operation, not a silently skipped endpoint — the same is true of a multipart
body with no declared properties.

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

`RealSpecTest` runs the parser over `apps/demo-api/openapi.yaml` — 44 operations across 7 tags — so a
parser change that breaks on a real document fails here rather than in a consuming module. It finds
the spec by walking up to the directory holding `project.yaml`, so it does not care what the working
directory is.
