# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read [AGENTS.md](AGENTS.md) first** — it holds the shared build commands, module layout, and catalog conventions. The notes below are Claude Code specific.

Skills live in `.agents/skills/` (the cross-client Agent Skills convention); `.claude/skills` is a symlink to that directory, so both this repo's other agents and Claude Code load the same files. Edit the real files under `.agents/skills/`.

## Skills

- **`kotlin-toolchain`** before writing or debugging a `module.yaml`/`project.yaml`, adding a dependency, or wiring a module; **`ktorfit`** for Ktorfit API interfaces. Both carry a `references/` cache of their official docs — read the relevant page rather than answering from memory, since both move faster than memory does.
- **`skill-from-docs`** to add a skill for another library or tool, or to refresh a cached one. It owns the token budget rules that every skill here follows.
- **`large-feature-branch-workflow`** when a feature needs more than one PR.

## Working here

- Prefer `./kotlin show modules` / `show settings -m <module>` / `show dependencies -m <module>` over reading manifests and inferring. They resolve the real model in seconds and surface manifest errors with a line pointer, without a compile.
- Verify load-bearing toolchain claims against the CLI before writing them into docs or skills. Build a throwaway project in the session scratchpad — never inside `libs/` or `plugins/` — and confirm with `./kotlin show`.
- The same rule applies to a library's runtime behaviour, and it is cheaper than it sounds: when a design turns on how a client behaves — which thread a callback arrives on, whether two calls may overlap — write the spec that asks it before writing the design. `ConsumerConfinementTest` and `ConfirmThreadsTest` each need no server, run in milliseconds, and each replaced a confident wrong answer. AGENTS.md has the two they corrected.
- Never pipe a `./kotlin` command into `tail`/`grep` — the pipeline reports the filter's exit code, so a failed build reads as success. Redirect to a file, echo `$?`, then read the file. A KSP or compile failure was masked this way twice in this repo.
- Check `docker ps` before starting a container, and stop what you start. The workspace's services do not all fit at once — the Kafka cluster alone is ~1.9 GiB, and starting it alongside the rest once put this machine into the OOM killer, which killed the user's IDE rather than anything of ours. AGENTS.md has the rest under Performance.
- Give build and test commands a generous timeout. The first `./kotlin build`/`./kotlin test` after a toolchain change downloads the compiler, a JRE, and dependencies into `~/.cache/JetBrains/Kotlin`, which can far exceed the default two-minute Bash timeout.
- Never introduce Gradle files to "fix" a build. If something needs a build feature this repo lacks, it belongs in a toolchain plugin module under `plugins/`, not in a `build.gradle.kts`.
- Run `ktlint -F --relative "**/*.kt" "!build/**"` before committing Kotlin changes. The exclusion matters — without it ktlint lints generated output and reports thousands of violations in files nobody edits.
- Document a capability in the same change that adds it, in the file that owns that audience —
  support for a new OpenAPI keyword, format or extension goes in `docs/openapi-support.md`, not in
  a module README. AGENTS.md has the table.
- Keep files short and single-purpose and follow SOLID — AGENTS.md spells out what each principle means in this repo. If a change makes a file mix two concerns, split the file in the same change rather than leaving it for later.
- Coroutines first; when a Java API leaves no choice but an actual thread — `Runtime.addShutdownHook` takes one — write `Thread.ofVirtual().unstarted { }`, never `Thread(…)` and never `startVirtualThread` for a hook. The latter starts immediately, so the hook is registered dead or refused, and the JVM swallows either outcome silently. AGENTS.md has the reasoning and `ContainerService` the spec.
- A `shared-jpa` query says what it loads. Associations are annotated `LAZY` — Hibernate Reactive
  has no transparent lazy loading, so an unfetched one throws rather than costing a second select —
  and the query names what the caller needs with `fetch` / `fetchEach`, or projects the columns and
  loads no entity. `EAGER` is not the shortcut it looks like: it is the N+1, measured at three
  secondary fetches for three rows in `FetchJoinTest`. AGENTS.md's Performance section has the rule
  and `docs/jpa-criteria.md` the vocabulary. `fetch`/`fetchEach` are for a query; an
  `entityGraph<T>()` is for a `find`, which has no query to join on, and for nesting more than one
  level. Either way `limit`/`offset` silently truncate once a collection is being loaded — nothing
  refuses the combination, so it is on the caller to page owners and collections separately.
- Look in `libs/shared-common` before writing a helper, and move one there when a second module wants it — it holds what is reusable across libraries and apps, and depends on nothing but kotlinx. AGENTS.md explains which of its three concurrency types fits a given caller; the short version is that a Java callback cannot take a `Mutex`, so it gets a `Mailbox`.
- A Ktor integration assumes the resource is not its own: it takes an `instance` as well as a config, closes only what it opened, and can register what it installed with the DI container (`injectable = true`) so a class built by that container is not forced through `call.x`. Anything `AutoCloseable` closes through `CloseGuard` — Ktor's DI closes what it hands out and cannot be told not to, so a second close has to be harmless. AGENTS.md's *Shared code* section has all three rules.
- New code goes under `com.strange.*` — see the package rule in AGENTS.md. Nothing new should use the old `dev.nxgt` prefix.
- Add dependencies by adding a catalog alias to `libs.versions.toml` and referencing `$libs.<alias>` from the module — never paste raw versioned coordinates into a `module.yaml`, and remember `$libs.bundles.*` does not work here.
