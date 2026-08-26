---
name: kotlin-toolchain
description: Building this repo with the JetBrains Kotlin Toolchain (`kotlin` CLI, formerly Amper): project.yaml/module.yaml schema, the libs.versions.toml catalog, templates, testing, publishing. Use when writing a manifest or adding a dependency.
---

# Kotlin Toolchain

This repo builds with the JetBrains Kotlin Toolchain (`kotlin` CLI), **not Gradle or Maven**. Never add `build.gradle.kts`, `settings.gradle.kts`, or `pom.xml` to "fix" a build.

`references/` holds the official documentation for the pinned docs version, cached as markdown. Read the relevant page there before answering from memory — the toolchain is young and its schema changes between releases. Refresh it with the `skill-from-docs` skill.

## Commands

```bash
kotlin build [-m <module>] [-v release]   # compile + link (debug variant by default)
kotlin test [-m <module>]                 # run tests
kotlin check [-m <module>]                # run checks; `kotlin show checks` lists them
kotlin run -m <module>                    # run an application module
kotlin publish <repository-id>            # e.g. mavenCentral, or an id from `repositories:`
kotlin clean                              # drop build/ and project caches
```

Single test:

```bash
kotlin test --include-test com.example.MyTest.myTestMethod
kotlin test --include-test 'com.example.MyTest/Nested.myTestMethod'   # '/' separates nested classes
kotlin test --include-classes 'com.example.*ServiceTest'              # wildcards, repeatable
```

Inspect the resolved model instead of guessing — these are fast and catch manifest errors without compiling:

```bash
kotlin show modules
kotlin show settings -m <module>       # effective config after templates are merged
kotlin show dependencies -m <module>
kotlin show tasks | kotlin show checks | kotlin show commands
```

## Project and module shape

`project.yaml` at the repo root lists modules by path. Explicit entries are what the docs recommend; globs work too.

```yaml
modules:
  - libs/core
  - plugins/some-plugin
```

Each module is a directory with a `module.yaml`, sources in `src/`, tests in `test/`, test-only resources in `testResources/`:

```yaml
product: jvm/lib          # see references/user-guide-product-types-*.md

apply:
  - //common.module-template.yaml

dependencies:
  - //libs/core                     # another module, path from the project root
  - $libs.kotlinx.serialization     # project catalog (libs.versions.toml)
  - $kotlin.reflect                 # toolchain catalog
  - io.ktor:ktor-client-core:3.5.2  # raw coordinates — avoid; put it in the catalog

test-dependencies:
  - $libs.kotest.runner.junit5

settings:
  kotlin:
    ...
test-settings:                      # test-only overrides; inherits from settings
  kotlin:
    ...
```

Key rules that are easy to get wrong:

- **Module paths start with `//`** and are relative to the project root (`//libs/core`). A bare `libs/core` is read as an *external* dependency. Relative forms need an explicit leading `.` (`./nested`, `../sibling`) and the docs say they may be deprecated — use `//`.
- **`module.yaml` is schema-validated.** An unknown property fails the whole command with a line pointer, so `kotlin show modules` doubles as a cheap syntax check after editing a manifest.
- Dependencies are **not transitive at compile time**. A dependent module sees a library only if the producing module marks it `exported: true` (the equivalent of Gradle's `api()`).
- Scopes are `all` (default), `compile-only`, `runtime-only`.
- Tests use kotlin-test with **JUnit 5** by default on JVM/Android; change via `settings.junit` (`junit-5`, `junit-4`, `none`).

## Dependency reuse: catalog + templates

`libs.versions.toml` at the repo root is the project catalog and the single source of truth for versions. Add every commonly used dependency there and reference it as `$libs.<key>`; do not paste versioned coordinates into a `module.yaml`. Alias keys map with dashes becoming dots: `kotest-runner-junit5` → `$libs.kotest.runner.junit5`.

**The toolchain does not support `[bundles]`.** `$libs.bundles.<name>` fails with `No catalog value for the key`; only `[versions]` and `[libraries]` are read. The bundles in this repo's catalog are inert for toolchain modules — treat them as documentation of which stack a dependency belongs to, and as the contract for Gradle-based consumers.

The toolchain-native way to share a dependency *set* across modules is a **template**: a `<name>.module-template.yaml` with the same shape as `module.yaml` (but no `product:`), pulled in via `apply:`. Templates merge dependencies, settings, and repositories, and can apply other templates. Verify the result with `kotlin show settings -m <module>`.

## Toolchain version

The wrapper scripts (`./kotlin`) pin the toolchain version per project; without them the CLI warns `Found a project.yaml ... but the wrapper script is missing` and falls back to whatever is on `PATH`. Create/refresh them with `kotlin update -c [--target-version=<v>]` and commit them, then prefer `./kotlin <command>`.

The toolchain ships its own compiler and stdlib. The `kotlin = "..."` entry in the catalog is for consumers that need an explicit version — it does not decide what this repo compiles with.

## Reference index

`references/INDEX.md` maps every cached page to its topic and source URL. Highest-value entries:

| Topic | File |
| --- | --- |
| Project/module concepts, layout, path notation | `user-guide-basics.md` |
| `project.yaml` schema | `reference-project.md` |
| `module.yaml` schema (every setting) | `reference-module.md` |
| Dependencies, scopes, catalogs | `user-guide-dependencies.md` |
| Shared config via templates | `user-guide-templates.md` |
| Tests, JUnit selection, test settings | `user-guide-testing.md` |
| Library products | `user-guide-product-types-jvm-lib.md`, `user-guide-product-types-kmp-lib.md` |
| Publishing to Maven repositories | `user-guide-publishing.md` |
| CLI commands and provisioning | `cli.md`, `cli-provisioning.md` |
| Writing toolchain plugins (for `plugins/`) | `user-guide-plugins-overview.md`, `user-guide-plugins-topics-*.md` |
| Built-in tech (Ktor, kotlinx-rpc, serialization, Compose, Spring) | `user-guide-builtin-tech-*.md` |
| KSP, compiler plugins, annotation processing | `user-guide-advanced-*.md` |
| Migrating a module from Gradle/Maven | `getting-started-migrating-from-gradle.md`, `getting-started-migrating-from-maven.md` |
