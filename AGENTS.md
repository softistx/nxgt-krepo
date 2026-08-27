# AGENTS.md

Shared guidance for any coding agent working in this repository.

## What this repo is

`nxgt-krepo` is a multi-module Kotlin library repository built with the **JetBrains Kotlin Toolchain 0.12+** (the `kotlin` CLI, formerly Amper) — *not* Gradle and *not* Maven. There is no `build.gradle.kts`, no `settings.gradle.kts`, and no `gradlew`.

What exists:

| Path | Role |
| --- | --- |
| `project.yaml` | Project manifest — lists the modules, and registers local toolchain plugins |
| `libs.versions.toml` | Project catalog: every dependency the modules share |
| `./kotlin`, `kotlin.bat` | Toolchain wrappers pinning the CLI version |
| `libs/openapi-generator` | Reads an OpenAPI spec, emits models and a typed client with KotlinPoet |
| `libs/shared-mongo` | MongoDB for a Kotlin coroutine service: query extensions, keyset pagination, a CRUD repository and service, GridFS |
| `libs/shared-redis` | Redis for a Kotlin coroutine service, over Lettuce: a namespaced connection, a typed cache, a lock, topics and streams |
| `libs/shared-storage` | S3-compatible object storage over the MinIO SDK: buckets, objects, and presigned URLs and upload forms |
| `plugins/openapi` | Toolchain plugin wrapping the generator as a build task |
| `apps/demo-api` | Ktor server implementing a slice of `apps/demo-api/openapi.yaml` |
| `apps/demo-client` | Generates a Ktorfit client from that spec and calls the server |
| `apps/demo-spring-client` | Generates a Spring `@HttpExchange` client from the same spec |
| `.agents/skills/` | Kotlin Toolchain reference + docs-sync skills (see below) |

A module is a directory with a `module.yaml`, registered by path in `project.yaml`.

## The OpenAPI generator

Two modules and one reference document — read those before changing either module:

- [`docs/openapi-support.md`](docs/openapi-support.md) — what the generator understands of a
  document, and what each part becomes in Kotlin. This is the file that grows.

- [`libs/openapi-generator`](libs/openapi-generator/README.md) — the generator. swagger-parser reads
  the spec into an intermediate representation, and a `SourceEmitter` turns that into KotlinPoet
  files. The root package holds only that IR; `parser` reads, `emit` holds the emitter contract and
  what every emitter shares, `models` emits the schemas, and `ktorfit` and `spring` are the two
  client styles — the parser knows about none of them. The type layer models `allOf`, `oneOf`/`anyOf`
  (discriminated or deduced), enums, `nullable`, `default`, typed `additionalProperties` and the
  common `format`s; inline schemas are promoted to named components in a pass that runs before
  anything else reads the document, so every later stage only ever resolves a name.
- [`plugins/openapi`](plugins/openapi/README.md) — the toolchain plugin around it:
  typed `@Configurable` settings, one `@TaskAction`, and a `generated.sources` entry so the output
  compiles into the consuming module.

A module opts in from its own `module.yaml`:

```yaml
plugins:
  openapi:
    enabled: true
    client: Ktorfit                       # or Spring, or None for models only
    specFile: ../demo-api/openapi.yaml
    packageName: com.strange.demo.client.api
```

Everything else has a default: `groupBy: Tag`, `models: Auto`, `interfacePrefix: ""`,
`interfaceSuffix: "Api"`. Grouping by tag turns `categories-controller` into `CategoriesApi`. The
spec's schemas are always generated; only the API surface is optional. `models` decides what binds
them — `Auto` follows the client, and a Ktorfit client is always kotlinx.serialization.

For Ktorfit the generated interfaces are then picked up by `ktorfit-ksp`, which generates the
`createXxxApi()` builders — plugin-generated sources do reach KSP. For Spring there is no
processing step; the interfaces go to `HttpServiceProxyFactory` at runtime.

Both demo apps drive their generated client against the real `demo-api` server over HTTP:
`apps/demo-client` through Ktorfit and kotlinx.serialization, `apps/demo-spring-client` through a
`HttpServiceProxyFactory` proxy and Jackson 3 — which also pins down that a Jackson client and a
kotlinx server read the same document the same way. `HttpServiceProxyFactory` builds an AOP proxy
and formats argument values, so a Spring client module needs `spring-aop` and `spring-context`
alongside `spring-web`; neither arrives transitively. Its conversion service also writes an enum
argument with `Enum.name()`, so a Spring client registers the generated `ApiEnumConverters.kt` with
the factory — without it every enum path, query or header parameter goes out as the Kotlin name.

The generator also reads vendor extensions: `x-kotlin-name` renames anything it would otherwise
derive, `x-kotlin-type` binds a schema to a type the consumer owns, `x-kotlin-value-class` turns a
scalar alias into a `@JvmInline value class`, and `x-kotlin-skip`/`x-internal`,
`x-enum-varnames`/`x-enumNames`, `x-enum-descriptions`, `x-deprecated-reason` and `x-nullable` do
what their names say. **An unrecognised `x-kotlin-*` key fails the parse**, naming the nearest key
that exists; everything outside that namespace is ignored, because it belongs to another toolchain.

## Instruction files

- **`AGENTS.md`** (this file) — the shared, tool-agnostic instructions. Codex, Cursor, Gemini CLI, Zed, Aider and friends read it natively.
- **`CLAUDE.md`** — Claude Code's entry point; it points here and adds only Claude-specific notes.
- **`.agents/skills/`** — skills in the [Agent Skills](https://agentskills.io) `SKILL.md` format, at the cross-client convention path. `.claude/skills` is a symlink to it so Claude Code (which scans its own directory) sees the same files; there is one copy, not two.

## Skills

The skills in `.agents/skills/` carry this repo's working knowledge; use them instead of reasoning from memory:

- **`kotlin-toolchain`** — manifest schema, catalog and template rules, commands, plus `references/`: a markdown cache of the full official documentation (50 pages, version recorded in `references/INDEX.md`).
- **`ktorfit`** — the Ktorfit HTTP client, including how it is wired up here through KSP alone, without its Gradle plugin.
- **`skill-from-docs`** — builds and refreshes docs-backed skills. Each such skill declares its source in a `docs-source.json`; refresh one with:
  ```bash
  python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill <name>
  ```
  Run it after a version bump, or whenever a cached page disagrees with the tool. Files under `references/` are generated — fix the script, not the output.
- **`large-feature-branch-workflow`** — two-level branching for work too large for a single PR.

Skills are budgeted: a `description` is in context every session (keep it ≤250 chars), a SKILL.md body loads on activation (≤~120 lines), and `references/` pages load only when opened. Put cost in the deepest tier that can hold it.

## Commands

The toolchain finds the project by walking up from the working directory, so these work anywhere inside the repo.

```bash
./kotlin build                      # compile + link everything
./kotlin build -m <module>          # one module (repeatable)
./kotlin build -v release           # debug is the default variant
./kotlin test                       # run all tests
./kotlin check                      # run all checks; ./kotlin show checks lists them
./kotlin run -m <module>            # run an application module
./kotlin publish <repository-id>    # e.g. mavenCentral, or an id from the repositories list
./kotlin clean                      # drop build/ and project caches
```

Running a single test:

```bash
./kotlin test --include-test com.example.MyTest.myTestMethod
./kotlin test --include-test 'com.example.MyTest/Nested.myTestMethod'   # '/' separates nested classes
./kotlin test --include-classes 'com.example.*ServiceTest'              # wildcard pattern, repeatable
```

Inspecting the resolved project model — cheap, and it catches manifest errors without a compile:

```bash
./kotlin show modules                    # module names accepted by -m
./kotlin show settings -m <module>       # effective config after templates merge
./kotlin show dependencies -m <module>   # proves a dependency actually resolves
./kotlin show tasks
```

### Toolchain wrapper

`./kotlin` and `kotlin.bat` are committed wrappers pinning the toolchain to the `kotlin_cli_version` at the top of the script (0.12.0). **Use `./kotlin <command>`, not a bare `kotlin`**, so everyone builds with the same version regardless of what is on `PATH`. Regenerate with `kotlin update -c` (add `--target-version=<v>` to move the pin).

### Local services

The databases this workspace runs against are **already containerised and usually already up** —
`~/workspace/docker/apps/` holds one compose file per service: `database/mongo` is an `rs0` replica
set on `localhost:27017`, transactions included, `database/redis` is Redis Stack on
`localhost:6379`, and `minio` is an S3-compatible store on `localhost:9000`. Check `docker ps` before pulling an image or starting a Testcontainers container:
the pull costs a gigabyte and the second container either clashes on the port or silently tests a
different server than the one everything else uses.

Integration tests therefore point at the running service — `MONGO_TEST_URI` and `REDIS_TEST_URI`,
defaulting to `mongodb://localhost:27017` and `redis://localhost:6379/15` — and skip themselves when
it is unreachable, so a machine without it reports skipped tests rather than a red build.

The storage specs are the exception to the defaulting: `MINIO_TEST_ENDPOINT` defaults to
`http://localhost:9000`, but `MINIO_TEST_ACCESS_KEY` and `MINIO_TEST_SECRET_KEY` have **no
defaults** and the specs skip when they are unset. A credential with a default is a credential in
source control. They live in `~/workspace/docker/apps/minio/.env`; export them for the run and never
commit them:

```bash
set -a; . ~/workspace/docker/apps/minio/.env; set +a
MINIO_TEST_ACCESS_KEY=$MINIO_ROOT_USER MINIO_TEST_SECRET_KEY=$MINIO_ROOT_PASSWORD ./kotlin test -m shared-storage
```

They also have to leave the server as they found it, because it is not theirs: the Mongo specs use a
database per spec and drop it, the Redis specs use database 15 with a key namespace per spec and
delete it, and the storage specs create a bucket per scenario and empty and remove it. Nothing here
calls `FLUSHDB`, and nothing touches a bucket it did not create.

## Module layout

Sources in `src/`, tests in `test/`, test-only resources in `testResources/`:

```yaml
# libs/<name>/module.yaml
product: jvm/lib          # or kmp/lib, jvm/app, ...

apply:
  - //common.module-template.yaml   # shared config, see below

dependencies:
  - //libs/core                     # another module — path from the project root
  - $libs.kotlinx.serialization     # project catalog alias

test-dependencies:
  - $libs.kotest.runner.junit5
```

Register modules in `project.yaml` by path (explicit entries are what the docs recommend; globs such as `libs/*` also work):

```yaml
modules:
  - libs/core
  - libs/api
```

Rules that are easy to get wrong:

- **Module dependency paths start with `//`** and are relative to the project root. A bare `libs/core` is read as an *external* Maven coordinate; relative forms (`./nested`, `../sibling`) work but the docs warn they may be deprecated.
- `module.yaml` is schema-validated: an unknown property fails the command with a pointer to the offending line, so a passing `kotlin show modules` is a cheap syntax check after editing a manifest.
- Dependencies are **not transitive at compile time** — a dependent module sees a library only if the producing module marks it `exported: true` (Gradle's `api()`).
- Tests use kotlin-test with **JUnit 5** by default on JVM/Android; change via `settings.junit`.

## Dependency reuse

`libs.versions.toml` at the repo root is the project catalog and **the single place where dependency versions live**. Any dependency used by more than one module — and by default any dependency at all — goes in the catalog and is referenced from modules as `$libs.<alias>`; do not paste versioned Maven coordinates into a `module.yaml`. Alias keys map with dashes becoming dots: `kotest-runner-junit5` → `$libs.kotest.runner.junit5`. Toolchain-provided catalogs (`$kotlin.*`, `$compose.*`) need no catalog entry.

**The toolchain does not read `[bundles]`** — `$libs.bundles.<name>` fails with `No catalog value for the key`. The bundles in this catalog still document which stack a dependency belongs to and serve Gradle-based consumers, but modules must list individual aliases.

To share a dependency *set* or settings across modules, use a **module template**: a `<name>.module-template.yaml` with the same shape as `module.yaml` (minus `product:`), pulled in with `apply: [ //name.module-template.yaml ]`. Templates merge dependencies, settings, and repositories, and can apply other templates; check the result with `kotlin show settings -m <module>`.

The catalog's `[bundles]` groupings map to the intended consumer surfaces of this library, which is the fastest way to see which stack a new module belongs to:

- **`compose`** / `compose-test` / `compose-android-test` / `compose-ksp` — Android + Compose client: Material3, Navigation/Compose Destinations, Room, DataStore, WorkManager, Koin, Apollo, kotlinx-rpc client.
- **`ktor`** / `ktor-mix` / `ktor-test` — server: Ktor, kotlinx-rpc server, MongoDB Kotlin coroutine driver, Koin, Konform validation, Nimbus JOSE JWT, Spring Security crypto, simple-java-mail, Kotest.
- **`shared`** / `shared-test` — code common to both: kotlinx-serialization, kotlinx-rpc client, bson-kotlinx.
- **`kotlinx`**, **`faker`** — coroutines/datetime, and kotlin-faker for test data.

The catalog's `kotlin = "2.4.0"` entry is for consumers that need an explicit Kotlin version; the toolchain supplies its own compiler and stdlib (2.4.10 with CLI 0.12.0), so that entry does not control what this repo compiles with.

## Conventions

- **Formatting is ktlint's job**, configured by `.editorconfig` at the repo root (wildcard imports
  are allowed there; everything else is ktlint's `ktlint_official` style). `./kotlin check` does
  *not* run it — `./kotlin show checks` lists only `tests` — so run it yourself before committing,
  excluding generated output:

  ```bash
  ktlint --relative "**/*.kt" "!build/**"        # report
  ktlint -F  --relative "**/*.kt" "!build/**"    # fix what it can
  ```

  Without the `!build/**` exclusion it lints KSP and plugin output and drowns you in thousands of
  violations in files nobody edits. The `filename` rule is the one `-F` cannot fix: a file's name
  must be PascalCase, so `Main.kt`/`DemoServer.kt`, never `main.kt`.
- **Documentation is written in the same change as the code**, not collected at the end. A branch
  that adds a capability adds its paragraph; a branch that changes a behaviour edits the paragraph
  that described the old one. A "what it does not handle" list that still describes a previous
  phase is worse than no list.
- **Each documentation file has one audience, and they do not mix.**

  | File | Answers |
  | --- | --- |
  | `README.md` | What is this repo, and where do I read next? Stays short. |
  | `docs/openapi-support.md` | What does the generator understand of an OpenAPI document? **This is where support for a new keyword, format or extension is documented** — it is the part that grows every phase. |
  | `libs/openapi-generator/README.md` | How is the module shaped, what does each emitter produce, how do I add one? Roughly constant in size. |
  | `plugins/openapi/README.md` | How do I turn this on in a module, and what does that need on its classpath? |
  | `libs/shared-mongo/README.md` | How is the Mongo library shaped, and why is each non-obvious part the way it is? |
  | `libs/shared-redis/README.md` | The same, for Redis — including what each layer deliberately does not do |
  | `libs/shared-storage/README.md` | The same, for object storage — and what a presigned URL can and cannot promise |
  | `AGENTS.md` | How do I work in this repo? One paragraph per capability, never the detail. |

  When a README section starts growing every phase, that is the signal it belongs in `docs/`, not
  the signal to keep appending. `libs/openapi-generator/README.md` reached 394 lines before its
  reference half moved out; splitting on *audience* rather than on length is what made the seam
  obvious.
- **Keep files short and single-purpose.** One file holds one concern; when two things could be
  separated cleanly, separate them. A file growing past roughly 150 lines is a signal to split it,
  not a threshold to argue with — split by responsibility, never by line count.
- **Follow SOLID, strictly.** In practice, here:
  - *Single responsibility* — the parser reads the spec, an emitter shapes output, the plugin wires
    it into the build. None of them does another's job.
  - *Open/closed* — a new client style is a new `SourceEmitter` and one `ClientKind` value; it must
    not require editing the parser or the existing emitters.
  - *Liskov* — every `SourceEmitter` is usable wherever the interface is, including the models-only
    one; no implementation may need special handling by its caller.
  - *Interface segregation* — keep interfaces narrow. `SourceEmitter` is one method because that is
    all a caller needs.
  - *Dependency inversion* — depend on the abstraction: the plugin's task action talks to
    `SourceEmitter`, and picks the implementation in exactly one place.
- **Tests are kotest `FeatureSpec`, grouped by scenario.** Every spec extends `FeatureSpec`, with
  `feature("...")` naming the behaviour under test and `scenario("...")` naming one case of it:

  ```kotlin
  class OpenApiParserTest :
      FeatureSpec({
          feature("grouping") {
              scenario("groups operations by tag, one interface per tag") { /* ... */ }
              scenario("falls back to the path segment when a tag is missing") { /* ... */ }
          }
      })
  ```

  Features are the unit of grouping, so a spec that would hold a single flat list of tests is
  telling you the feature names are missing, not that grouping does not apply. Nest a `feature`
  inside a `feature` when a case genuinely has sub-cases; do not reach for `context`, which belongs
  to the other spec styles. One spec class per file, named after the file.
- **Every module's packages start with `com.strange`.** The rest follows the module: `com.strange.openapi` for `libs/openapi-generator`, `com.strange.openapi.plugin` for `plugins/openapi`, `com.strange.demo.api` for `apps/demo-api`. Generated code follows the same rule — the `openapi` plugin's `packageName` setting is set per module, and defaults to `generated.api` only when nobody sets it.
- **Organise `src/` by package, not as a flat pile of files.** A module with more than one concern
  gets a directory per concern, and the directory matches the package — `src/parser/` is
  `com.strange.openapi.parser`. The root package holds only what every package depends on: the
  shared contract, nothing else. When a file lands in the root because it did not obviously belong
  anywhere, that is the signal a package is missing.
- `.gitignore` excludes `build`, `.idea`, and `.jbeval`; build output goes to `build/` under the project root unless `--build-dir` overrides it.
- Work on `develop`; `main` is the PR target.
