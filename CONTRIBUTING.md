# Contributing

Thanks for looking. This is a Kotlin monorepo built with the **JetBrains Kotlin Toolchain** — no
Gradle, no Maven, no `gradlew`. A module is a directory with a `module.yaml`, registered by path in
`project.yaml`. If you have only ever built Kotlin with Gradle, that is the one thing to internalise
before anything below makes sense.

[`AGENTS.md`](AGENTS.md) is the full working reference — every convention, with the reasoning. It is
long because it is written for coding agents as much as for people. This file is the short version:
what you need to open a pull request.

## Getting set up

You need **git**, **Docker** (for the integration specs), **Node 22 + pnpm** (for the release
tooling only), and **ktlint 1.8.0** — pinned, because two ktlint versions disagree about formatting
and CI runs that one.

You do **not** need a JDK or a Kotlin compiler: the `./kotlin` wrapper provisions both.

```bash
git clone https://github.com/softistx/nxgt-krepo.git
cd nxgt-krepo
pnpm install          # release tooling; the Kotlin build does not need it
./kotlin build        # the first run downloads a CLI, a JRE and every dependency — be patient
./kotlin test
```

Use `./kotlin`, never a bare `kotlin`: the wrapper pins the toolchain version.

```bash
./kotlin show modules              # every module name accepted by -m
./kotlin show settings -m stx-jpa  # the resolved model, templates applied
./kotlin build -m stx-jpa          # one module
./kotlin test --include-test com.softistx.jpa.FetchJoinTest
```

Prefer `./kotlin show` over reading manifests and inferring — it resolves the real model in seconds
and points at the line when a manifest is wrong.

### The integration specs need nothing of you

Each backing service resolves in one order: an environment variable if it names a server, otherwise
a container started once for the run, otherwise the spec skips and says so. You do not have to
export anything or start anything — `./kotlin test` works on a machine where nothing is running.

If you already have one of these up, point at it (`MONGO_TEST_URI`, `POSTGRES_TEST_URI`, …) and the
specs will use it instead of pulling an image. `libs/stx-testing/README.md` has the list.

## Opening a pull request

**Every pull request targets `develop`.** Branch off `develop`, open against `develop`, merge there.
Nothing is merged into `main`, however small and however green — `main` is aligned from `develop`
when someone asks for it. A PR opened against `main` has the wrong base and wants recreating.

The one exception is a feature too large for a single PR: its slices target that feature's own
integration branch, and the integration branch targets `develop`.

Commit subjects are **Conventional Commits**, scoped by module, with `!` for a break:

```
feat(stx-graphix): collect exception handlers from Spring, Koin and Ktor
fix(stx-jpa): the foreign key is a column, not a second select
refactor(stx-graphix)!: one handler function, not a class of @ExceptionMapping
```

### Before asking for a review

```bash
ktlint -F --relative "**/*.kt" "!build/**"   # the exclusion matters — see below
./kotlin build && ./kotlin test
```

Without `!build/**`, ktlint lints KSP and plugin output and reports thousands of violations in files
nobody edits. `./kotlin check` does *not* run ktlint — it runs tests only.

**If you touched `libs/`**, two more things:

```bash
pnpm changeset                                          # CI fails the PR without one
./kotlin publish mavenLocal -m <library> --non-transitive
./kotlin build -m <an-example-that-uses-it>
```

The second pair is not ceremony. No module under `examples/` or `server/` may name a `//libs/...`
dependency: they resolve `io.github.softistx:stx-*` from mavenLocal through the catalog. A module
reference proves the sources compile together, which the library's own specs already prove; a
*published coordinate* proves the thing nothing else here can — that the POM names what a consumer
needs, at the scope a consumer needs it. Both times a published artifact was wrong in this
repository, an example is what found it.

So a green `./kotlin build -m <example>` says nothing about your change unless the publish came
first. Say which of the two you actually ran.

## What reviewers will look for

- **Documentation in the same change as the code.** Not collected at the end. AGENTS.md has a table
  mapping every documentation file to its single audience — a new query operator goes in
  `docs/jpa-criteria.md`, a new `stx.*` key in `docs/spring-configuration.md`, never in a README.
  A "what it does not handle" list still describing a previous phase is worse than no list.
- **Short, single-purpose files, and SOLID.** A file past roughly 150 lines is a signal to split by
  responsibility. If a change makes a file mix two concerns, split it in the same change.
- **Coroutines first.** When a Java API leaves no choice but a thread, `Thread.ofVirtual()`.
- **Specs are kotest `FeatureSpec`**, `feature("…")` naming the behaviour and `scenario("…")` one
  case of it. One spec class per file, named after the file. Fixtures live in a package named for
  what they are, not beside the spec that needed them first.
- **Check Material 3 before writing a `libs/stx-material` component**, and reuse the catalog before
  adding a dependency — `libs.versions.toml` is the single place a version lives, referenced as
  `$libs.<alias>`. Never paste a versioned coordinate into a `module.yaml`.
- **Everything new is `com.softistx.*`.** That is the Kotlin package. The Maven *group* is
  `io.github.softistx` — they are independent and both are correct.
- **Never add a Gradle file to fix a build.** If the build needs a capability this repository lacks,
  it belongs in a toolchain plugin under `plugins/`.

## Releasing

You do not, unless you are a maintainer — but knowing the shape explains why the changeset is
required. [`docs/releasing.md`](docs/releasing.md) has it.

## Reporting something instead

A bug or an idea: [open an issue](https://github.com/softistx/nxgt-krepo/issues/new/choose).
A vulnerability: **not** an issue — see [SECURITY.md](SECURITY.md).

By contributing you agree your work is licensed under [Apache-2.0](LICENSE), and to the
[Code of Conduct](CODE_OF_CONDUCT.md).
