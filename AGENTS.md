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
| `libs/openapi-codegen` | Reads an OpenAPI spec, emits a typed client with KotlinPoet |
| `plugins/openapi-client` | Toolchain plugin wrapping the codegen as a build task |
| `apps/demo-api` | Ktor server implementing a slice of `apps/demo-api/openapi.yaml` |
| `apps/demo-client` | Generates its client from that spec and calls the server |
| `.agents/skills/` | Kotlin Toolchain reference + docs-sync skills (see below) |

A module is a directory with a `module.yaml`, registered by path in `project.yaml`.

## The OpenAPI client generator

`libs/openapi-codegen` is a plain `jvm/lib` and holds all the work: swagger-parser reads the
spec into an intermediate representation, and a `ClientEmitter` turns that into KotlinPoet
files. `KtorfitEmitter` is the only implementation today; a Spring `HttpExchange` emitter is
the next one, and the IR exists so the parser never has to know which.

`plugins/openapi-client` is a thin `jvm/amper-plugin` around it: typed `@Configurable`
settings, one `@TaskAction`, and a `generated.sources` entry so the output compiles into the
consuming module. A module opts in from its own `module.yaml`:

```yaml
plugins:
  openapi-client:
    enabled: true
    specFile: ../demo-api/openapi.yaml   # relative to the module root
    packageName: dev.nxgt.demo.client.api
```

Everything else has a default: `client: Ktorfit`, `groupBy: Tag`, `generateModels: true`.
Grouping by tag turns `categories-controller` into `CategoriesApi`.

For Ktorfit, the generated interfaces are then picked up by `ktorfit-ksp`, which generates the
`createXxxApi()` builders — plugin-generated sources do reach KSP. `apps/demo-client` shows the
whole chain, and its `EndToEndTest` drives it against the real `demo-api` server over HTTP.

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

- `.gitignore` excludes `build`, `.idea`, and `.jbeval`; build output goes to `build/` under the project root unless `--build-dir` overrides it.
- Work on `develop`; `main` is the PR target.
