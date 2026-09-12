---
name: ktorfit
description: Ktorfit — the KSP-generated HTTP client for Kotlin Multiplatform (@GET/@POST interfaces, converters, the Ktorfit builder), plus how to wire it up in this repo without its Gradle plugin. Use when writing or debugging a Ktorfit API interface.
---

# Ktorfit

An HTTP client for Kotlin Multiplatform built on Ktor clients, in Retrofit's shape: declare an interface, annotate the functions, and KSP generates the implementation. `references/` caches the official docs (<https://foso.github.io/Ktorfit/>); read the relevant page there instead of guessing.

## Shape of the thing

```kotlin
interface ExampleApi {
    @GET("people/1/")
    suspend fun getPerson(): String
}

val ktorfit = Ktorfit.Builder().baseUrl("https://swapi.dev/api/").build()
val api = ktorfit.createExampleApi()          // generated extension function
```

Every function needs an HTTP-method annotation, and functions must be `suspend` unless they return `Flow` or `Call` (which need their converter factories registered). `baseUrl` must end in `/`; an annotation value starting with `http` replaces it.

KSP generates `_ExampleApiImpl` plus a `createExampleApi()` extension, in the interface's own package, under `build/generated/<module>/main/src/ksp/kotlin/`.

## Using Ktorfit in this repo

This project builds with the Kotlin Toolchain, which has **no Gradle plugin support** — and Ktorfit's documented install starts with its Gradle plugin. It still works, through KSP alone. Verified against toolchain 0.12.0 with Ktorfit 2.7.5:

```yaml
# libs/<role>/<name>/module.yaml
product: jvm/lib            # or kmp/lib — Ktorfit targets JVM, Android, JS, iOS, Linux

dependencies:
  - $libs.ktorfit.lib       # or ktorfit-lib-light to pick your own Ktor engines

settings:
  kotlin:
    ksp:
      processors:
        - $libs.ktorfit.ksp # KSP processors accept catalog aliases
```

Add both to `libs.versions.toml` (`de.jensklingenberg.ktorfit:ktorfit-lib` and `:ktorfit-ksp`, same version) per the repo rule that versions live in the catalog. Ktorfit is Ktor-client based, so it belongs with the `shared`/`compose` groupings rather than the server bundles.

**Call `createExampleApi()`, never `create<ExampleApi>()`.** The generic form is rewritten by Ktorfit's *compiler* plugin, which the Gradle plugin applies and this build system does not. It still compiles here — Ktorfit emits a warning saying the call "will not trigger the compiler plugin" — so the failure is silent at build time. The standalone `de.jensklingenberg.ktorfit:compiler-plugin` artifact stopped publishing at 2.3.5 while the library is at 2.7.5, so there is no matching version to declare. If Ktorfit resumes publishing it, the toolchain can apply it via `settings.kotlin.compilerPlugins` (see `../kotlin-toolchain/references/user-guide-advanced-kotlin-compiler-plugins.md`).

## Constraints that only surface at build or run time

- **A `@Part` parameter may not be nullable.** ktorfit-ksp fails the build with `Part parameter type may not be nullable`, so an optional multipart field still has to be declared non-null.
- **A `@Body` request needs a `Content-Type`.** Ktor's ContentNegotiation alone is not enough: without one the call fails at runtime with `Fail to prepare request body for sending ... with Content-Type: null`. Put `@Headers("Content-Type: application/json")` on the function, or set a default on the `HttpClient`.
- Deserializing responses needs `ContentNegotiation` with `json()` installed on the `HttpClient` you hand to `Ktorfit.Builder().httpClient(...)`, plus an engine (`$ktor.client.cio`).

`ktorfit-lib` pins Ktor 3.5.0; Ktor plugins added alongside it (serialization, auth, logging) must be compatible with that line.

## Reference index

`references/INDEX.md` lists everything. The pages worth knowing:

| Topic | File |
| --- | --- |
| All request annotations: `@Url`, `@Query`, `@Path`, `@Header`, `@Body`, `@FormData`, `@Multipart`, `@Streaming`, `@Tag`, `RequestBuilder` | `requests.md` |
| Converter types and the built-in factories | `converters-converters.md` |
| `Flow` and `Call` return types | `converters-responseconverter.md` |
| Suspend + parameter converters | `converters-suspendresponseconverter.md`, `converters-requestparameterconverter.md` |
| Writing a custom converter, end to end | `converters-example1.md` |
| Ktorfit builder, own Ktor client, `QualifiedTypeName` | `configuration.md` |
| What KSP generates, and how | `architecture.md`, `generation.md` |
| KMP single-target gotcha | `knownissues.md` |
| Android R8/ProGuard rules | `android-proguard.md` |
| Upgrading from 1.x | `migration.md`, `converters-migration.md` |

Note `configuration.md` opens with Gradle-specific advice that does not apply here; its useful half starts at the "Ktorfit Builder" heading.
