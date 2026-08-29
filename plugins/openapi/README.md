# openapi

A Kotlin Toolchain plugin that generates Kotlin sources from an OpenAPI document at build time: the
spec's schemas always, and a typed HTTP client when you ask for one. It is a thin wrapper — all the
work is in [`libs/stx-openapi-generator`](../../libs/stx-openapi-generator/README.md), and this module
contributes the task, the typed settings, and the `generated.sources` entry that makes the output
part of the consuming module's compilation.

For what the generator makes of a document — type mapping, composition, enums, vendor extensions,
and what it does not handle — see [`docs/openapi-support.md`](../../docs/openapi-support.md). This
README is about turning it on and what that needs on your classpath.

The plugin is registered once, in `project.yaml`:

```yaml
plugins:
  - ./plugins/openapi
```

## Using it

Enable it from the `module.yaml` of the module that should hold the generated code:

```yaml
plugins:
  openapi:
    enabled: true
    client: Ktorfit                       # or Spring, or None for models only
    specFile: ../demo-api/openapi.yaml
    packageName: com.strange.demo.client.api
```

| Setting | Default | Meaning |
| --- | --- | --- |
| `specFile` | `openapi.yaml` | Path to the document, relative to the module root. May point outside it. |
| `packageName` | `generated.api` | Root of the generated output; nothing is written here directly — see [where the output goes](#where-the-output-goes). Set it — the default only exists so `enabled: true` alone works. |
| `client` | `Ktorfit` | `Ktorfit`, `Spring`, or `None`. Decides what is generated, and therefore what the module needs on its classpath. |
| `groupBy` | `Tag` | `Tag`, `Path` or `None`. `Tag` turns `categories-controller` into `CategoriesApi`. Ignored when `client: None`. |
| `models` | `Auto` | `Auto`, `Kotlinx` or `Jackson`. `Auto` follows the client. |
| `interfacePrefix` | `""` | Prepended to every interface name — `"I"` gives `ICategoriesApi`. |
| `interfaceSuffix` | `"Api"` | Appended to every interface name — `"Client"` gives `CategoriesClient`. |

Models are always generated; they are the part of the document every consumer needs, and making them
optional only ever produced a client whose payload types came from somewhere else.

`models` decides what binds them:

| `client` | `models: Auto` | Overridable? |
| --- | --- | --- |
| `Ktorfit` | kotlinx.serialization | No. `models: Jackson` fails the build. |
| `Spring` | Jackson 3 | Yes — `models: Kotlinx` for a `WebClient` using `KotlinSerializationJsonEncoder`. |
| `None` | kotlinx.serialization | Yes — either. |

Ktorfit is fixed because it deserializes through Ktor's `ContentNegotiation`, which this repo
configures with kotlinx.serialization; generating Jackson models for it would produce a client that
compiles and then fails at the first response. Asking for that combination is an error, not a
setting the plugin quietly overrides:

```
ERROR: Task ':demo-client:generate@openapi' failed: java.lang.IllegalArgumentException:
openapi: client Ktorfit always generates kotlinx.serialization models;
remove `models: Jackson`, or switch to `client: Spring`
```

Settings are typed and KDoc'd in `src/settings.kt`, so the IDE completes them and an unknown key
fails `./kotlin show modules` with a line pointer rather than at build time.

## What each choice needs from the consuming module

**`client: Ktorfit`** — the generated interfaces are only half of it. `ktorfit-ksp` reads them and
generates the `createXxxApi()` builders, which works because plugin-generated sources are fed to KSP:

```yaml
dependencies:
  - $libs.ktorfit.lib
  - $ktor.client.cio                 # an engine
  - $ktor.client.contentNegotiation
  - $ktor.serialization.kotlinx.json

settings:
  ktor: enabled
  kotlin:
    serialization: json
    ksp:
      processors:
        - $libs.ktorfit.ksp
```

Call `createCategoriesApi()`, never `create<CategoriesApi>()` — see the `ktorfit` skill for why the
generic form silently misbehaves here. `examples/demo-client` is the worked example.

Two generated Ktor plugins go on the `HttpClient` the `Ktorfit` instance is built with. Neither is
installed for you, because the `HttpClient` is yours:

```kotlin
HttpClient(CIO) {
    install(ContentNegotiation) { json() }
    install(ApiErrors)                        // if the document declares any typed failure
    install(ApiAuth) { bearer = { token() } } // if it declares any security scheme
}
```

Without `ApiErrors`, Ktor leaves `expectSuccess` off and a `404` carrying an error body is handed to
the deserializer as the success type — what surfaces is a complaint about a body of the wrong shape,
with no status and no parsed body. Without `ApiAuth`, nothing attaches a credential.

**`client: Spring`** — no processing step; `$libs.spring.web` is enough to compile, and the
interfaces are handed to `HttpServiceProxyFactory` at runtime. Because the functions are `suspend`,
the proxy needs a reactive adapter (`WebClientAdapter`, from spring-webflux) rather than
`RestClientAdapter`. Models are Jackson 3 by default, so add `$libs.jackson.module.kotlin` to read
their primary constructors — note Jackson 3 lives under the `tools.jackson` group id, while its
annotations deliberately stayed at `com.fasterxml.jackson.core`. `examples/demo-spring-client` is the
worked example.

Two runtime dependencies do not arrive transitively behind `spring-web`, and both fail as a
`NoClassDefFoundError` on the first call rather than at compile time: `$libs.spring.aop` (the proxy
is an AOP proxy) and `$libs.spring.context` (argument values are formatted through a conversion
service).

If the document declares any enum, the generator also emits `ApiEnumConverters.kt`, and the module
has to wire it into the factory — one line, but a required one:

```kotlin
val conversions = DefaultFormattingConversionService().also(::registerApiEnumConverters)
HttpServiceProxyFactory.builderFor(adapter).conversionService(conversions).build()
```

Spring writes an enum argument with `Enum.name()`, never `toString()`, so without it a path, query
or header parameter goes out as the Kotlin name — `IN_PROGRESS` where the document says
`in-progress`. That request succeeds and matches nothing, which is why it is called out here rather
than left to be discovered.

A Spring client's equivalent is three lines rather than two, because Spring splits the job: the
proxy factory knows the method, the `WebClient` sees the request and the response, and the operation
has to travel between them.

```kotlin
val credentials = ApiAuthConfig().apply { bearer = { token() } }
val webClient = WebClient.builder()
    .clientConnector(JdkClientHttpConnector())
    .baseUrl(url)
    .filter(apiAuthFilter(credentials))   // if the document declares any security scheme
    .filter(apiErrorFilter())             // if it declares any typed failure
    .build()

HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient))
    .conversionService(conversions)
    .httpRequestValuesProcessor(apiOperationProcessor())   // always, if either filter is installed
    .build()
```

`apiOperationProcessor()` is what makes the other two work: it reads `@ApiOperation` off the
interface method and puts the operation in a request attribute. Leave it out and both filters run
with no idea which operation they are looking at — the error filter falls back to the untyped
`ApiException` for everything, and the auth filter attaches nothing. Neither says so at build time,
which is why it is called out here.

`apiErrorFilter()`'s default mapper is `JsonMapper.builder().findAndAddModules().build()`. A bare
`JsonMapper` has no jackson-module-kotlin and binds a generated data class to an object whose every
property is null — a parsed error body that silently says nothing. Pass the `WebClient`'s own mapper
if it is configured differently.

**`client: None`** — models only. They default to kotlinx.serialization, so the module needs
`settings.kotlin.serialization: json`; set `models: Jackson` instead if that is what binds them.
Use it where the API surface is hand-written or lives elsewhere but the payload types should still
follow the document.

**Any style, if the document declares an unfamiliar `http` security scheme** — `digest` or
`negotiate` — and an operation requires it: the emit fails naming the operation. The credential for
one of those is the answer to a server challenge, not a value the client can be handed. `oauth2` and
`openIdConnect` are fine: this generator does not run the flow, but the token a flow produces goes
in the same bearer header, and the slot is there for you to put it in.

**Any style, if the document uses `x-kotlin-type`** — the named type and its serializer belong to
the consuming module: kotlinx needs it `@Serializable`, Jackson needs it bindable. Nothing is
generated for such a schema, so a missing type is an ordinary unresolved reference.

**Any style, if the document uses `format: date`** — the kotlinx model style maps it to
`kotlinx.datetime.LocalDate`, so the module needs `$libs.kotlinx.datetime`. It is the only type
either style emits that is not already on the classpath the style implies; Jackson's side is
`java.time.LocalDate`, and `format: uuid` is `kotlin.uuid.Uuid` or `java.util.UUID`, both stdlib.

## Where the output goes

```
build/tasks/_<module>_generate@openapi/    what this plugin emits
build/generated/<module>/main/src/ksp/     what ktorfit-ksp then generates from it
```

Under the first, three packages below `packageName` — and nothing directly in it:

```
<packageName>.apis      CategoriesApi, TagsApi, …            one per tag
<packageName>.models    Category, Tag, ErrorResponse, …      one per schema
<packageName>.utils     ApiOperation, ApiExceptions,         what the client needs underneath
                        ApiErrors, ApiAuth,
                        ApiProxySupport, ApiEnumConverters
```

So a document with a `tags` endpoint group *and* a `Tag` schema generates both without a collision,
and the two imports say which one you meant. The names are fixed and not configurable.

Two directories, because two stages ran. If the second is empty for a Ktorfit client, KSP never saw
the interfaces.

The task deletes its output directory before writing, so a renamed tag or a removed endpoint does
not leave a stale file behind.

## When it fails

Failures name the file and the reason, and they fail the build rather than emitting a partial
result:

```
ERROR: Task ':demo-client:generate@openapi' failed:
com.strange.openapi.parser.OpenApiParseException: could not parse /…/openapi.yaml:
malformed or unreadable swagger supplied
```

A missing spec, a blank `packageName`, an unsupported request media type, and a multipart body with
no declared properties are all reported the same way, as are two operations or two schemas whose
generated names would collide — the message names the pair rather than letting one overwrite the
other. `EmitException` is the neighbouring case: the document parsed, but the chosen client cannot
express something in it, such as a `TRACE` operation with `client: Spring`.

One trap when reading build output: never pipe `./kotlin` into `tail` or `grep`. The pipeline
reports the filter's exit code, so a failed KSP or compile stage reads as a successful build.
Redirect to a file and check `$?`.

## Adding a client style

See [the generator README](../../libs/stx-openapi-generator/README.md#adding-a-client-style). On this
side it is one value in `ClientKind` and one branch in `ClientKind.emitter()`, both in `src/`.
