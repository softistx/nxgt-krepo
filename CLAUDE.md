# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read [AGENTS.md](AGENTS.md) first** — it holds the shared build commands, module layout, and catalog conventions. The notes below are Claude Code specific.

Skills live in `.agents/skills/` (the cross-client Agent Skills convention); `.claude/skills` is a symlink to that directory, so both this repo's other agents and Claude Code load the same files. Edit the real files under `.agents/skills/`.

## Skills

- **`kotlin-toolchain`** before writing or debugging a `module.yaml`/`project.yaml`, adding a dependency, or wiring a module; **`ktorfit`** for Ktorfit API interfaces. Both carry a `references/` cache of their official docs — read the relevant page rather than answering from memory, since both move faster than memory does.
- **`openapi-spec-first`** before adding or changing a REST endpoint, in either stack. It owns the shape of the work:
  the document is split with redocly under `<module>/openapi/` and bundled to `api-docs.yaml` *before* the build reads
  it, a Spring Boot API is `stx-spring-boot` with a `@RestController` implementing the **generated** `@HttpExchange`
  interface, a Ktor API is `stx-ktor` with `client: None` and hand-written routes, and both lay out as
  repository → service → controller. A spec then drives the application through the very interfaces its
  controllers implement. Its two `references/` pages are hand-written, not fetched — read
  `spring-api.md` before writing a controller or its spec. `plugins/openapi/README.md` is what each
  `client` needs on the classpath; `docs/openapi-support.md` is what the generator makes of a document.
- **`compose-multiplatform`** before touching `libs/ui/stx-material` or `examples/material-demo`, and **`material3-compose`** before theming or extending a Material 3 component. The first records what this host actually verifies — `./kotlin build` skips the Apple targets on Linux, so a green local build proves nothing about them, while `./kotlin publish` cross-compiles both and writes real klibs — and the second is generated from the resolved jar, because `$compose.material3` sits on its own alpha version line and the androidx docs describe a different artifact.
- **Check Material 3 before writing a `libs/ui/stx-material` component.** M3 already has `Button`,
  `IconButton`, `ButtonGroup`, `Card`, the chips, `Badge`, `ListItem`, `Text`, `Icon`, `Surface`
  and the dividers — reuse and customise through its `*Defaults`/`*Colors` parameters rather than
  rebuilding from `Row` and `Modifier.background`, which throws away the ripple, the touch target
  and the semantics. `material3-compose`'s `references/components.md` is the list. AGENTS.md's
  *Building a component* has the rule and the shape of a wrapper — including that a claim about
  pixels is measured in pixels: `ImageComposeScene` renders a composable headlessly in
  milliseconds, and `libs/ui/stx-material/test@jvm/` holds the two specs that caught what the eye
  did not.
- **`styles`** is the pattern for what Material 3 cannot express, not background reading: a component's look is a `Style` in its own file, its interaction states are `pressed`/`hovered`/`disabled` blocks with `animate` inside them, and its signature carries one `style: Style = Style` instead of colour and shape parameters. AGENTS.md's *Styling a component* has the rules. **`adaptive`** and **`edge-to-edge`** come from the same catalogue and describe *Jetpack* Compose — check any API they name against `material3-compose`'s `references/components.md`, and read the two ways that search goes wrong before concluding something is missing.
- **`skill-from-docs`** to add a skill for another library or tool, or to refresh a cached one. It owns the token budget rules that every skill here follows. To pull one from Google's catalogue instead, `android skills add <name> --project=. --agent=common` — the `--agent=common` is what puts it in `.agents/skills/` rather than in your home directory. AGENTS.md's *Finding and installing a skill* has the three sources.
- **`large-feature-branch-workflow`** when a feature needs more than one PR — it owns the two-level shape: an integration branch off `develop`, a slice PR'd into it per step, then one PR from the integration branch to `develop`.

## Working here

- **Every PR targets `develop`, never `main`.** Branch off `develop`, `gh pr create --base develop`,
  merge it there. The one exception is a feature too large for a single PR: its slices target that
  feature's integration branch and the integration branch targets `develop` —
  `large-feature-branch-workflow` owns that shape. `main` is aligned from `develop` only when the user asks, and aligning means
  fast-forwarding `main` onto `develop` — not merging a feature branch into it. Check the base before
  opening a PR. The repo's default branch is now `develop`, so `gh pr create` picks the right base on
  its own — pass `--base develop` anyway rather than trusting a setting a fork or a stale clone may
  not share. AGENTS.md's *Conventions* section has the repair if `main` has been written to directly.
- Prefer `./kotlin show modules` / `show settings -m <module>` / `show dependencies -m <module>` over reading manifests and inferring. They resolve the real model in seconds and surface manifest errors with a line pointer, without a compile.
- Verify load-bearing toolchain claims against the CLI before writing them into docs or skills. Build a throwaway project in the session scratchpad — never inside `libs/` or `plugins/` — and confirm with `./kotlin show`.
- The same rule applies to a library's runtime behaviour, and it is cheaper than it sounds: when a design turns on how a client behaves — which thread a callback arrives on, whether two calls may overlap — write the spec that asks it before writing the design. `ConsumerConfinementTest` and `ConfirmThreadsTest` each need no server, run in milliseconds, and each replaced a confident wrong answer. AGENTS.md has the two they corrected.
- Never pipe a `./kotlin` command into `tail`/`grep` — the pipeline reports the filter's exit code, so a failed build reads as success. Redirect to a file, echo `$?`, then read the file. A KSP or compile failure was masked this way twice in this repo.
- Check `docker ps` before starting a container, and stop what you start. These services do not all fit at once on a 16 GiB laptop — the Kafka cluster alone is ~1.9 GiB, and starting it alongside the rest once put one into the OOM killer, which killed the running IDE rather than anything of ours. AGENTS.md has the rest under Performance.
- Give build and test commands a generous timeout. The first `./kotlin build`/`./kotlin test` after a toolchain change downloads the compiler, a JRE, and dependencies into `~/.cache/JetBrains/Kotlin`, which can far exceed the default two-minute Bash timeout.
- Never introduce Gradle files to "fix" a build. If something needs a build feature this repo lacks, it belongs in a toolchain plugin module under `plugins/`, not in a `build.gradle.kts`.
- Run `ktlint -F --relative "**/*.kt" "!build/**"` before committing Kotlin changes. The exclusion matters — without it ktlint lints generated output and reports thousands of violations in files nobody edits.
- Document a capability in the same change that adds it, in the file that owns that audience —
  support for a new OpenAPI keyword, format or extension goes in `docs/openapi-support.md`, a new
  `stx.*` property in `docs/spring-configuration.md`, a new Mongo operator or filter token in
  `docs/spring-mongo-queries.md` — not in a module README. A README answers *why the library is
  shaped this way* and stays roughly the size it is; the reference half is the one that grows.
  AGENTS.md has the table.
- Keep files short and single-purpose and follow SOLID — AGENTS.md spells out what each principle means in this repo. If a change makes a file mix two concerns, split the file in the same change rather than leaving it for later.
- Coroutines first; when a Java API leaves no choice but an actual thread — `Runtime.addShutdownHook` takes one — write `Thread.ofVirtual().unstarted { }`, never `Thread(…)` and never `startVirtualThread` for a hook. The latter starts immediately, so the hook is registered dead or refused, and the JVM swallows either outcome silently. AGENTS.md has the reasoning and `ContainerService` the spec.
- A `stx-jpa` query says what it loads. Associations are annotated `LAZY` — Hibernate Reactive
  has no transparent lazy loading, so an unfetched one throws rather than costing a second select —
  and the query names what the caller needs with `fetch` / `fetchEach`, or projects the columns and
  loads no entity. `EAGER` is not the shortcut it looks like: it is the N+1, measured at three
  secondary fetches for three rows in `FetchJoinTest`. AGENTS.md's Performance section has the rule
  and `docs/jpa-criteria.md` the vocabulary. `fetch`/`fetchEach` are for a query; an
  `entityGraph<T>()` is for a `find` or a `get` by identifier, which has no query to join
  on, and for nesting more than one level. Either way `limit`/`offset` silently truncate once a collection is being loaded — nothing
  refuses the combination, so it is on the caller to page owners and collections separately.
- A data-access library here offers extensions, not a base class: there is no repository or CRUD
  service in `stx-mongo` or `stx-jpa`, because a class cannot have a `reified` type parameter
  and an extension can — `session.findAll<Purchase>()` needs no `KClass` and no object to construct.
  What a base class used to earn is kept explicitly, in `insertAndRead`, the transaction guard on
  every JPA write verb, and `stampedBy`/`touchedBy`. AGENTS.md's *Shared code* section has the rest.
- Look in `libs/core/stx-common` before writing a helper, and move one there when a second module wants it — it holds what is reusable across libraries and apps, and depends on nothing but kotlinx. AGENTS.md explains which of its three concurrency types fits a given caller; the short version is that a Java callback cannot take a `Mutex`, so it gets a `Mailbox`.
- A Ktor integration assumes the resource is not its own: it takes an `instance` as well as a config, closes only what it opened, and registers what it installed with the DI container — unconditionally, no flag, `provideX` is `internal` — so a class built by that container is not forced through `call.x`. Anything `AutoCloseable` closes through `CloseGuard` — Ktor's DI closes what it hands out and cannot be told not to, so a second close has to be harmless. AGENTS.md's *Shared code* section has all three rules.
- New code goes under `com.softistx.*` — see the package rule in AGENTS.md. Nothing new should use the old `dev.nxgt` prefix.
- **A change under `libs/` carries a changeset, and the changeset names the family**: `bun
  changeset`, pick the families, commit the `.changeset/*.md` it writes. A *family* is a directory
  under `libs/<role>/` — a library and its framework integrations share one version, so a change to
  `stx-jpa-spring` releases `stx-jpa`. CI fails a PR without one, naming the families you changed
  and did not declare; `bun changeset --empty` is the explicit "nothing published changes". Never
  edit a family's `version:` in its `<family>.module-template.yaml` or its key in `[versions]` of
  `libs.versions.toml` by hand — `scripts/sync-version.ts` writes both from the family's
  `package.json`, and moving one without the other leaves an example resolving a coordinate nobody
  published. A new `module.yaml` under `libs/` applies its **family** template, never
  `//publishing.module-template.yaml` directly — that one carries no `version:` on purpose, so a
  module wired wrong fails at publish rather than publishing under someone else's version.
  `docs/releasing.md` owns the circuit. And **a new `//libs/...` dependency across families is also
  an edit to that family's `package.json`** — those `dependencies` are the propagation graph, and
  `bun scripts/graph.ts --check` derives it from the manifests and fails when the two disagree.
- The Maven group is `io.github.softistx`; the Kotlin package prefix is `com.softistx`. They are
  independent and both are correct — do not "fix" one to match the other.
- **`scripts/` is TypeScript, run by bun.** No build step and no emitted JavaScript — but bun strips
  types rather than checking them, so run `bun run typecheck` as well as `bun test scripts/` before
  committing a change there. CI runs both.
- Add dependencies by adding a catalog alias to `libs.versions.toml` and referencing `$libs.<alias>` from the module — never paste raw versioned coordinates into a `module.yaml`, and remember `$libs.bundles.*` does not work here.
