# openapi

A Kotlin Toolchain plugin that generates Kotlin sources from an OpenAPI document at build time: the
spec's schemas always, and a typed HTTP client when you ask for one. It is a thin wrapper — all the
work is in [`libs/openapi-generator`](../../libs/openapi-generator/README.md), and this module
contributes the task, the typed settings, and the `generated.sources` entry that makes the output
part of the consuming module's compilation.

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
| `packageName` | `generated.api` | Package for the interfaces; models go in `<packageName>.model`. Set it — the default only exists so `enabled: true` alone works. |
| `client` | `Ktorfit` | `Ktorfit`, `Spring`, or `None`. Decides what is generated, and therefore what the module needs on its classpath. |
| `groupBy` | `Tag` | `Tag`, `Path` or `None`. `Tag` turns `categories-controller` into `CategoriesApi`. Ignored when `client: None`. |
| `interfacePrefix` | `""` | Prepended to every interface name — `"I"` gives `ICategoriesApi`. |
| `interfaceSuffix` | `"Api"` | Appended to every interface name — `"Client"` gives `CategoriesClient`. |

Models are always generated; they are the part of the document every consumer needs, and making them
optional only ever produced a client whose payload types came from somewhere else.

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
generic form silently misbehaves here. `apps/demo-client` is the worked example.

**`client: Spring`** — no processing step; `$libs.spring.web` is enough to compile, and the
interfaces are handed to `HttpServiceProxyFactory` at runtime. Because the functions are `suspend`,
the proxy needs a reactive adapter (`WebClientAdapter`, from spring-webflux) rather than
`RestClientAdapter`. Models are Jackson-shaped, so a Jackson `ObjectMapper` with the Kotlin module
binds them. `apps/demo-spring-client` is the worked example.

**`client: None`** — models only, in kotlinx.serialization's shape, so the module needs
`settings.kotlin.serialization: json`. Use it where the API surface is hand-written or lives
elsewhere but the payload types should still follow the document.

## Where the output goes

```
build/tasks/_<module>_generate@openapi/    what this plugin emits
build/generated/<module>/main/src/ksp/     what ktorfit-ksp then generates from it
```

Two directories, because two stages ran. If the second is empty for a Ktorfit client, KSP never saw
the interfaces.

The task deletes its output directory before writing, so a renamed tag or a removed endpoint does
not leave a stale file behind.

## When it fails

Failures name the file and the reason, and they fail the build rather than emitting a partial
result:

```
ERROR: Task ':demo-client:generate@openapi' failed:
com.strange.openapi.OpenApiParseException: could not parse /…/openapi.yaml:
malformed or unreadable swagger supplied
```

A missing spec, a blank `packageName`, an unsupported request media type, and a multipart body with
no declared properties are all reported the same way.

One trap when reading build output: never pipe `./kotlin` into `tail` or `grep`. The pipeline
reports the filter's exit code, so a failed KSP or compile stage reads as a successful build.
Redirect to a file and check `$?`.

## Adding a client style

See [the generator README](../../libs/openapi-generator/README.md#adding-a-client-style). On this
side it is one value in `ClientKind` and one branch in `ClientKind.emitter()`, both in `src/`.
