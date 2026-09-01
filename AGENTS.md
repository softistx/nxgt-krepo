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
| `libs/stx-openapi-generator` | Reads an OpenAPI spec, emits models and a typed client with KotlinPoet |
| `libs/stx-common` | What more than one module needs and nothing else. Two concurrency packages, split by whether the caller can suspend: `coroutines/` has `CoroutineSafeMap`, `KeyedMutex` and `Mailbox`; `concurrent/` has `Memo`, `Guarded`, the `ConcurrentMap` extensions and the `BlockingQueue` bridge, for the Hibernate binders and SLF4J initialisers that cannot. Plus `CloseGuard`, the keyset-pagination half both stores share, and the one lenient `Json` the storage and messaging libraries read through |
| `libs/stx-amqp` | AMQP over the RabbitMQ client: topology in one block, publishes that wait for the confirm, deliveries as a `Flow`, and a delay-queue retry path |
| `libs/stx-i18n` | Message catalogs compiled once at startup, a per-key walk down the locale chain, ICU arguments and plurals, `Accept-Language` negotiation, and an audit of what each locale is missing |
| `libs/stx-jpa` | Postgres for a Kotlin coroutine service, over Hibernate Reactive: annotated Kotlin entities, sessions confined to the event loop that opened them, HQL, SQL and JPA Criteria — named by `KProperty` rather than by strings — through one suspending builder |
| `libs/stx-material` | The repo's one client-side library — Compose Multiplatform components over Material 3: `StxTheme` takes M3's own four inputs and wraps `MaterialExpressiveTheme`, component looks are declared as Compose `Style`s with their interaction states animated, and every curve comes from M3's `MotionScheme` rather than a hand-written `tween` |
| `libs/stx-kafka` | Kafka for a Kotlin coroutine service: suspending sends, records as a `Flow`, offsets committed after the handler, and an admin client |
| `libs/stx-ktor` | The Ktor seam: the resource-lifecycle idiom every plugin is built on (`own`, `publish`, `resource`, `required`), and the integrations that are that idiom applied — a connection per application opened and closed with it, and one negotiated locale per request. An integration with a design of its own is a module beside its library instead |
| `libs/stx-mongo` | MongoDB for a Kotlin coroutine service: CRUD collection extensions, keyset pagination, an opt-in audit trail, GridFS |
| `libs/stx-redis` | Redis for a Kotlin coroutine service, over Lettuce: a namespaced connection owning one `Json`, and kotlinx-serialized cache, lock, topics and streams |
| `libs/stx-spring-boot` | Spring Boot integration for the libraries here, a package per concern: translated errors in one response shape, the request's locale read off the exchange rather than a `ThreadLocal`, and every auto-configuration opt-in behind `stx.*` |
| `libs/stx-storage` | S3-compatible object storage over the MinIO SDK: buckets, objects, and presigned URLs and upload forms |
| `libs/stx-graphix` | GraphQL over graphql-java 25: annotated Kotlin functions, `@Serializable` types, suspending execution. `stx-graphix-ktor` and `stx-graphix-spring` are the HTTP integrations |
| `libs/stx-workflow` | Compensable workflows for a Kotlin coroutine service: a DSL of steps each with its own compensation, one `@Serializable` context threaded through them, and state checkpointed after every node so a process that dies mid-run is picked up where it stopped. `await` and `sleep` stop an instance for a signal or a deadline by writing it down rather than by holding a coroutine, and `workflowOf` reads the same declaration off an annotated class. `engine.find(Failed)` is the operator's inbox for the one outcome the engine refuses to resolve. Four modules in the group: the engine, `stx-workflow-db` for where instances live (Redis, SQL through `stx-jpa`, or MongoDB), and `stx-workflow-ktor` / `stx-workflow-spring` for the two framework integrations |
| `libs/stx-telemetry` | Logs and traces for a Kotlin coroutine service. The current span and the inherited fields are a `CoroutineContext.Element`, not a `ThreadLocal` — the same argument `RequestTranslator` makes about `LocaleContextHolder`, applied to the tool that is supposed to make such bugs visible. `TelemetryContext` is also a `ThreadContextElement`, so the runtime keeps a thread-local mirror in step and `log.info(…)` needs not suspend. An event is a `@Serializable` type; `Mailbox` is the queue and one coroutine drains it to the exporters |
| `libs/stx-telemetry-otlp` | OTLP/HTTP+JSON export for `stx-telemetry`, on `java.net.http.HttpClient` with hand-written `@Serializable` documents. No OpenTelemetry SDK — it would bring its own `ThreadLocal` `Context`. `OtlpExporterTest` pins the document against a JDK `HttpServer`; a second spec gated on `OTLP_TEST_ENDPOINT` asks a real collector, because a stub agrees with whatever we wrote |
| `libs/stx-telemetry-slf4j` | The SLF4J bridge for `stx-telemetry`, both ways. Inbound is an `SLF4JServiceProvider` found by SPI, so a third-party library's logs enter the pipeline carrying the current span; it reads the caller's MDC, which is correct because it is read on the very thread that wrote the line and never carried across a hop. Outbound is an `Slf4jExporter` for an application that keeps logback. Both at once is a loop, and the exporter refuses to be built when the bound provider is this module's own |
| `libs/stx-telemetry-mongo` | MongoDB export for `stx-telemetry`. A batch is one unordered `insertMany`, and the document is what `signalJson` produces plus the resource, with the three instants written back as BSON dates — JSON has no date, and a string is not something Mongo will expire or index. Retention is a TTL index built on the first batch, because building it suspends and nothing that constructs an exporter does; a changed retention drops and rebuilds it rather than leaving the old window silently in place. It depends on the driver and deliberately not on `stx-mongo`, which is sessions, pagination and GridFS |
| `libs/stx-telemetry-ktor` | Ktor plugin for `stx-telemetry`: `install(Observability)`. Unlike every other plugin here it intercepts `ApplicationCallPipeline.Monitoring` rather than using `on(CallSetup)` — a `CoroutineContext.Element` is only in scope inside the `withContext` that installed it, so the span has to wrap `proceed()`. The span is renamed to the matched route on `RoutingRoot.RoutingCallStarted`, which is why `SpanScope.name` is settable |
| `libs/stx-telemetry-spring` | Spring Boot auto-configuration for `stx-telemetry`, opt-in behind `stx.telemetry.enabled`. The server span is a `CoWebFilter` and not a `WebFilter`: a `WebFilter` returns a `Mono`, so what it puts in scope lives in the Reactor context, which a suspending `@RestController` method does not read. Needs `kotlinx-coroutines-reactor`, which the WebFlux starter does not bring — found by specs that hung, not by reading a manifest |
| `libs/stx-testing` | Test-only support the libraries share: the backing services their integration specs need, reused from the environment or started as containers for the run |
| `plugins/openapi` | Toolchain plugin wrapping the generator as a build task |
| `plugins/dgs-codegen` | Toolchain adapter of Netflix DGS codegen — GraphQL schema to Kotlin types |
| `plugins/apollo` | Toolchain adapter of Apollo Kotlin codegen — schema and documents to Kotlin models |
| `examples/demo-api` | Ktor server implementing a slice of `examples/demo-api/openapi.yaml` |
| `examples/demo-client` | Generates a Ktorfit client from that spec and calls the server |
| `examples/demo-spring-client` | Generates a Spring `@HttpExchange` client from the same spec |
| `examples/jpa-shop` | A Ktor catalogue over Postgres showing `stx-jpa`'s CRUD extensions and audit layer |
| `examples/graphix-shop` | A Ktor GraphQL catalogue showing `stx-graphix-ktor`: split SDL under `resources/graphql/`, annotated DataFetchers, SSE subscriptions |
| `examples/graphix-codegen` | Both GraphQL codegen plugins on one schema: DGS types and an Apollo `OPERATION_DOCUMENT` |
| `examples/spring-orders` | A Spring Boot order book over MongoDB showing `stx-spring-boot` with no configuration class: a spec-first REST API whose controllers implement the generated `@HttpExchange` interfaces, translated failures, keyset paging, an audit trail and two migrations — and `stx-telemetry-spring`, a server span per request with the service's own span nesting inside it and typed events into a collection |
| `examples/workflow-checkout` | A checkout saga over `stx-workflow` and Redis: a step with its compensation, a fan-out whose legs are undone one by one, and a run whose process is killed between the charge and the checkpoint so the idempotency key has something to prove |
| `examples/material-demo` | The `stx-material` catalogue — one Compose Multiplatform app in three modules: `md-catalog` holds every story, `md-desktop` and `md-android` are launchers |
| `.agents/skills/` | Kotlin Toolchain reference + docs-sync skills (see below) |

A module is a directory with a `module.yaml`, registered by path in `project.yaml`.

## The OpenAPI generator

**How a document is authored and laid out is the `openapi-spec-first` skill** — the split under
`<module>/openapi/`, the redocly commands, and what a controller built from the output looks like.
This section is the generator itself.

Two modules and one reference document — read those before changing either module:

- [`docs/openapi-support.md`](docs/openapi-support.md) — what the generator understands of a
  document, and what each part becomes in Kotlin. This is the file that grows.

- [`libs/stx-openapi-generator`](libs/stx-openapi-generator/README.md) — the generator. swagger-parser reads
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
    packageName: com.softistx.demo.client.api
```

Nothing is written to `packageName` itself: interfaces go to `<packageName>.apis`, schemas to
`.models`, and the client machinery — the operation annotation, the exception hierarchy, the plugins
and filters — to `.utils`. Fixed, not configurable, and the reason a document can have both a `tags`
endpoint group and a `Tag` schema.

Everything else has a default: `groupBy: Tag`, `models: Auto`, `interfacePrefix: ""`,
`interfaceSuffix: "Api"`. Grouping by tag turns `categories-controller` into `CategoriesApi`. The
spec's schemas are always generated; only the API surface is optional. `models` decides what binds
them — `Auto` follows the client, and a Ktorfit client is always kotlinx.serialization.

For Ktorfit the generated interfaces are then picked up by `ktorfit-ksp`, which generates the
`createXxxApi()` builders — plugin-generated sources do reach KSP. For Spring there is no
processing step; the interfaces go to `HttpServiceProxyFactory` at runtime.

Both demo apps drive their generated client against the real `demo-api` server over HTTP:
`examples/demo-client` through Ktorfit and kotlinx.serialization, `examples/demo-spring-client` through a
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

It also reads the half of an operation that is not the happy path. Every non-2xx response becomes
a typed failure: one generated exception per error *schema*, so `ErrorResponseException` carries a
parsed `ErrorResponse`, with a base `ApiException(status, rawBody)` for a status the document did
not declare, one it declared without a body, and a body that does not parse. `securitySchemes` and
`security` become `ApiAuthConfig`, one suspending credential slot per declared scheme, resolved
against the document root — `security: []` on an operation means *no* credential, not the root's.

Both need the same thing: the operation has to reach the HTTP layer, where the status and the body
live but the operation is anonymous. Every generated function therefore carries `@ApiOperation(id,
security)`, added once in `emit/ApiFile.kt`, and each style reads it its own way — Ktorfit through
`HttpRequest.annotations` in a client plugin, Spring through an `HttpRequestValues.Processor` that
reads the reflective `Method` and puts the operation in a request attribute. **Both were proved
against the running demo server before being written**, which is also how the Spring default mapper
turned out to need `findAndAddModules()`: without it Jackson binds a generated data class to an
object whose every property is null.

The wiring is generated but installed by the consumer, because the consumer owns the HTTP client —
`install(ApiErrors)` / `install(ApiAuth)` for Ktorfit, `apiErrorFilter()` / `apiAuthFilter()` plus
`apiOperationProcessor()` for Spring. The same split as `ApiEnumConverters.kt`.

## Instruction files

- **`AGENTS.md`** (this file) — the shared, tool-agnostic instructions. Codex, Cursor, Gemini CLI, Zed, Aider, Grok and friends read it natively.
- **`CLAUDE.md`** — Claude Code's entry point; it points here and adds only Claude-specific notes. Grok loads it too.
- **`.grok/rules/`** — Grok-specific notes. Grok scans every `*.md` here; shared conventions stay in this file.
- **`.agents/skills/`** — skills in the [Agent Skills](https://agentskills.io) `SKILL.md` format, at the cross-client convention path. `.claude/skills` and `.grok/skills` are symlinks to it so Claude Code and Grok Build see the same files; there is one copy, not two.

## Skills

The skills in `.agents/skills/` carry this repo's working knowledge; use them instead of reasoning from memory:

- **`kotlin-toolchain`** — manifest schema, catalog and template rules, commands, plus `references/`: a markdown cache of the full official documentation (50 pages, version recorded in `references/INDEX.md`).
- **`ktorfit`** — the Ktorfit HTTP client, including how it is wired up here through KSP alone, without its Gradle plugin.
- **`compose-multiplatform`** — the UI stack behind `libs/stx-material`: which platforms a Compose library may declare, what a non-Apple host does and does not verify, the real `$compose.*` catalog keys, and how kotest runs from a common `test/` tree. `references/` caches 59 pages of the official documentation.
- **`material3-compose`** — the Material 3 API surface that actually compiles here. Its `references/` are *not* fetched: `$compose.material3` resolves to its own alpha version line, so the pages are generated from the resolved jar by `scripts/extract_api.py`.
- **`skill-from-docs`** — builds and refreshes docs-backed skills. Each such skill declares its source in a `docs-source.json`; refresh one with:
  ```bash
  python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill <name>
  ```
  Run it after a version bump, or whenever a cached page disagrees with the tool. Files under `references/` are generated — fix the script, not the output.
- **`openapi-spec-first`** — how a REST API is authored here: a Redocly-split OpenAPI document under `<module>/openapi/`, the file and naming conventions the generator reads, and the repository → service → controller layering over the generated `@HttpExchange` interface. Read it before adding or changing an endpoint. Its `references/` are written by hand rather than fetched — `document-layout.md` for the split document and the redocly config, `spring-api.md` for the five files a Spring API is made of — and they stay in step with `examples/spring-orders`, which is the same thing running.
- **`large-feature-branch-workflow`** — how to split work too large for a single PR into slices that each land on one integration branch, which then lands on `develop` as the feature.

Three come from Google's [`android/skills`](https://github.com/android/skills) catalogue rather than being written here. They describe **Jetpack Compose (`androidx.compose.*`)**, and `libs/stx-material` builds on **Compose Multiplatform (`org.jetbrains.compose.*`)** — an API named in one of them may not exist in the version that compiles here, so check it against `material3-compose`'s `references/components.md` before using it:

- **`styles`** — the Compose Styles API. **This is the default pattern for every component in `libs/stx-material`**, not background reading; see *Styling a component* below.
- **`adaptive`** — window sizes, pointer and keyboard input, multi-pane layouts.
- **`edge-to-edge`** — drawing behind the system bars, for the demo's Android launcher.

## Building a component

**Check Material 3 first, and reuse it.** A component here is a thin layer that gives an M3
component this repo's vocabulary — it is not a reimplementation. Rebuilding one from `Row`,
`Column` and `Modifier.background` throws away its ripple, its minimum touch target, its
semantics, its RTL handling and its expressive shape morphing, and every one of those has to be
re-earned by hand and will be got wrong.

`material3-compose`'s `references/components.md` is the list to check — it is generated from the
resolved jar, so it says what actually compiles here rather than what the androidx docs describe.
Material 3 already has `Button`, `IconButton`, `ButtonGroup`, `Card` / `ElevatedCard` /
`OutlinedCard`, `FilterChip` / `AssistChip` / `InputChip`, `Badge`, `ListItem`, `Text`, `Icon`,
`Surface` and the dividers.

The shape of a wrapper:

- **Colour, shape, elevation, padding and border go through M3's own parameters** —
  `ButtonDefaults.buttonColors(…)`, `CardDefaults.cardColors(…)`, `shape = shapes.large`. Our
  tokens compute the argument; M3 does the painting. That is what keeps a plain M3 component and
  one of ours identical inside the same theme.
- **What M3 does not express stays a `Style`** applied on the outer `Modifier` — a press scale, a
  hover tint beyond the ripple, an alpha. A `Style` that sets `background` or `shape` on top of an
  M3 component is painting twice; that is the sign the value belonged in a `*Colors` instead.
- **Build from primitives only when M3 has nothing.** `Alert`, `EmptyState`, `Skeleton` and
  `ResponsiveButton` are ours because Material 3 has no equivalent, and each says so in its KDoc.

A claim about **pixels is measured in pixels.** `ImageComposeScene` renders a composable into a
bitmap with no window, in milliseconds, on a headless host, and `sendPointerEvent` drives hover and
press. `libs/stx-material/test@jvm/` holds the two specs that exist, and both were written
because something looked right and was not: a hover state that was invisible on *selected* chips
because they already wear the focus layer, and a collapsed `ResponsiveButton` that kept the padding
its label had left behind. Rendering specs are jvm-only — skiko's native library comes from
`$compose.desktop.currentOs` under `test-dependencies@jvm`.

## Styling a component

What is left after M3 has taken colour, shape and padding is dressed with the **Compose Styles
API**
(`androidx.compose.foundation.style`), not with colour parameters and `Modifier` chains. It ships
in Compose Multiplatform 1.11.1 — experimental, in `foundation` rather than `material3` — and the
module opts in once:

```yaml
settings:
  kotlin:
    optIns: [ androidx.compose.foundation.style.ExperimentalFoundationStyleApi ]
```

The shape a component takes:

- **Its look is a `Style`, in its own file** — `button/ButtonStyles.kt` holds `buttonStyle(variant,
  color)` and `button/Button.kt` holds no colours at all. A style reaches theme tokens through the
  `StyleScope` extensions in `theme/StyleTokens.kt` (`colors`, `scheme`, `shapes`, `spacing`,
  `motion`), which read the `CompositionLocal`s at resolve time rather than closing over whatever
  was in scope when the style was built.
- **Interaction states are declared, not wired.** `pressed { }`, `hovered { }`, `focused { }`,
  `disabled { }` sit inside the style, and `animate { }` inside those makes the transitions free.
  This replaces `animateColorAsState`/`animateFloatAsState` at the call site — a `Modifier.pressScale`
  helper was written here and deleted the same day the API landed, because the style block does it
  better and in one place.
- **The signature carries no styling parameters.** No `backgroundColor`, no `shape`, no
  `contentPadding`. Instead one `style: Style = Style` parameter, defaulting to exactly `Style`
  (the companion, which is the empty style) and applied *last* so a caller's override wins:
  `Modifier.styleable(styleState, base, style)`.
- **Presentation state belongs to the component.** `rememberUpdatedStyleState(interactionSource) {
  it.isEnabled = enabled }` gives pressed, hovered and focused for nothing; the caller passes
  business state and never remembers a boolean for a visual.
- **Every curve comes from Material 3's `MotionScheme`.** `StxMotion` holds the scheme
  `StxTheme` installed, and offers M3's two axes — `spatial(speed)` for anything that moves,
  which is a spring and may overshoot, and `effects(speed)` for colour and alpha, which must land
  exactly. Nothing writes `tween(300)` or names an easing. Giving a fade and a slide one curve is
  the mistake this replaced: the combined transition finishes in two stages. It is *held* rather
  than read from the composition because a `Style` block runs at apply time, not in a composable
  scope.
- **Every default has a name in `StxStyles`**, reached as `StxTheme.styles.card(variant)`.
  It is a plain `object` behind an extension property, not a `CompositionLocal` — a `Style` reads
  its tokens when it is applied, not when it is written — and it lives in `src/style/` so `theme`
  keeps knowing nothing about the components. Restating a default before editing it is what stops a
  one-off drifting away from the rest of the screen.

The `styles` skill has the full vocabulary, the state-animation guide and the migration workflow.
Three things it does not say, all established here:

- The API is in `foundation`, so grepping `material3` for it finds nothing.
- `styleable` is a **function**, so it compiles into `StyleModifierKt` and grepping class names for
  it also finds nothing. Either empty grep reads as proof of absence and is not.
- **`then` needs its own import.** `styleA then styleB` is a top-level infix extension in
  `androidx.compose.foundation.style`, not a member — without `import
  androidx.compose.foundation.style.then` the only candidate in scope is `Comparator.then`, and the
  compiler reports a return-type mismatch against `Comparator` rather than a missing import. The
  variadic `Style(a, b, c)` factory and `Modifier.styleable(state, vararg styles)` compose without
  it.

## Finding and installing a skill

Three sources, in the order worth trying:

1. **Google's Android catalogue**, through the `android` CLI. `android skills list` names what is
   available and `android skills find <keyword>` searches it. Install into this repo with:

   ```bash
   android skills add <name> --project=. --agent=common
   ```

   **`--agent=common` is the part that matters**: it writes to `.agents/skills/<name>`, which is
   this repo's convention and what `.claude/skills` symlinks to. Omitting it installs into every
   agent directory the CLI detects, including `~/.claude/skills`, where the skill is invisible to
   everyone else working here. The skill name is positional — a `--skill=<name>` form appears in
   Google's current guide but the installed CLI rejects it; `android skills add` with no arguments
   prints the usage its own version accepts.
2. **The `find-skills` skill**, for anything outside that catalogue.
3. **`skill-from-docs`**, when no published skill exists and the knowledge lives in a
   documentation site — or, as with `material3-compose`, in the artifact itself.

Whichever the source, an installed skill is committed like any other file: skills live in the repo
so that every agent and every person working here loads the same ones.

A skill's `references/` do not have to come from a docs site. When the published documentation describes a different version than the one that compiles — `material3-compose` is the case here — generating the pages from the artifact is the accurate option, and it follows the same rule: the output is generated, so fix the script rather than the page.

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
./kotlin publish -m stx-mongo mavenLocal   # publish one library; see Publishing below for why -m
./kotlin clean                      # drop build/ and project caches
```

## Publishing

The `libs/*` modules publish as `com.softistx:<module-name>:<version>` — `com.softistx:stx-mongo:0.1.0`
today. The configuration lives once in `publishing.module-template.yaml` at the repo root, which each
library pulls in with `apply: [ //publishing.module-template.yaml ]`; nothing about publishing is
written per module. `artifactId` is deliberately not set, because it defaults to the module's name —
so the directory name *is* the artifact name and there is no second place to keep in sync. The
module's `description:` becomes the POM `<description>`, which is the other reason every library has
one.

**`stx-spring-boot` is the one library not named `stx-<technology>`, and the suffix is deliberate.**
Spring reserves the `spring-boot*` prefix for itself and asks a third party for its own namespace,
naming an auto-configuration module `<ns>-spring-boot` and a dependency-only aggregator
`<ns>-spring-boot-starter`. This module is the first: it ships `AutoConfiguration.imports` and the
configuration metadata, and every optional library behind it is `compile-only`, so it hands a
consumer no opinionated dependencies — which is the one job a starter has. Merging the two is
allowed only for an auto-configuration with no optional features, and this one is almost entirely
optional features. `stx-spring` was also the wrong half of the name: nothing here works outside
Spring Boot.

```bash
./kotlin publish -m stx-mongo --transitive mavenLocal    # one library and what it depends on
./kotlin publish $(ls libs | sed 's/^/-m /') mavenLocal  # all of them
```

Three things about it that are not guessable:

- **`kotlin publish <id>` with no `-m` fails**, and not on the modules being published: it walks
  *every* module in the project and stops at the first one without that repository id —
  `Module 'demo-api' does not have repository with id 'mavenLocal'`. The examples are not products
  and must not carry a publishing block, so a selection is always passed.
- **Publishing is all-or-nothing across a dependency chain.** The toolchain refuses a module
  configured for publishing that depends on one that is not — `ERROR: Module 'stx-mongo' is
  configured for publishing but depends on module 'stx-common' which is not` — with a pointer at the
  offending dependency line. That is why every `libs/*` module publishes, `stx-testing` included.
- **A `kmp/lib` publishes one artifact per platform** plus a root one: `stx-material`,
  `stx-material-jvm`, `stx-material-android`, `stx-material-iosarm64`,
  `stx-material-iossimulatorarm64`. Its `composeResources` are *not* in the publication yet
  (KTC-5698) — the jar publishes, the resources do not.

`mavenLocal` needs no credentials, no PGP key and no POM metadata. A real repository is one more
block in the same template, changing nothing in any module. The feature is a preview in the
toolchain and its docs say it is likely to change.

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

### Shared code

`libs/stx-common` holds what **more than one module** needs, expressed without knowing anything
about any of them. It depends on kotlinx-coroutines and kotlinx-serialization and on nothing else,
ever: the moment something in there knows what a topic or a collection is, every library depending
on it inherits that, and a shared module that depends on everything is a cycle waiting for its
second commit.

Look there before writing a helper, and move one there when a *second* caller appears — not in
anticipation of one. A helper with a single caller belongs next to it, where it can be read
alongside the code that explains why it exists.

The concurrency types are not interchangeable, and the **first** question is which package: can the
caller suspend? `coroutines/` is for the ones that can — `CoroutineSafeMap` when each operation
stands alone, `KeyedMutex` when the work behind a key suspends and only callers wanting the *same*
key should wait, `Mailbox` when a producer is not a coroutine at all and must not be made to block.
`concurrent/` is for the callers that genuinely cannot suspend — a Hibernate binder, an SLF4J static
initialiser, a shutdown hook — and holds `Memo` for a value computed once per key, `Guarded` for an
object that is not thread-safe, and the `ConcurrentMap` and `BlockingQueue` extensions.
`libs/stx-common/README.md` has the reasoning; the short version is that reaching for `runBlocking`
to move from the second package to the first is how a client deadlocks against its own I/O thread.

**`getOrPut` on a `ConcurrentHashMap` is not atomic**, and this repository shipped that mistake in
`stx-jpa`'s serializer cache before it was written down. `kotlin.collections.getOrPut` compiles
against a concurrent map and is a get, a compute and a put with nothing holding them together, so two
threads at a cold key both run the loader — a wasted computation when the value is a plain result,
and a bug the moment it has identity. Reach for `Memo`, or `getOrCompute` when the loader differs per
call site. `MemoTest` pins the difference rather than asserting it.

**A data-access library offers extensions, not a base class to inherit from.** Neither
`stx-mongo` nor `stx-jpa` has a repository or a CRUD service class; the create/read/update/
delete vocabulary is extensions on `MongoCollection<T>` and on the JPA session. Two reasons, and the
first is the one that decides it: an extension takes `T` from its receiver or reifies it at the call
site, while a class cannot have a `reified` type parameter and so has to be handed a `KClass` or a
property reference to read an owner off — `session.findAll<Purchase>()` against
`JpaRepository(Purchase::id).findAll(session)`. And a repository over a collection turned out to be
one-line delegation twenty times over, with the overridable hooks its service used being the least
reusable part of either module. What a base class earned and an extension still has to provide is
kept explicitly: `insertAndRead`, the transaction guard on every JPA write verb, and the audit
stamps. When a new store is added, follow the same shape.

**Shared does not mean everything shared goes there.** `libs/stx-i18n` is used by more than
one module and is still its own library, because ICU4J is a 15 MB jar and `stx-common`'s rule
is kotlinx-and-nothing-else — putting message formatting in it would make `stx-kafka` carry a
formatting library it will never call. The same test applies to the next candidate: if it brings
a dependency, it brings that dependency to everything.

**A framework integration is a sibling module of the library it integrates**, in that library's own
group: `stx-workflow-ktor` and `stx-workflow-spring` beside `stx-workflow`, `stx-graphix-ktor` and
`stx-graphix-spring` beside `stx-graphix`.

Not in the framework module, because that makes it know every library in the repository: `stx-ktor`
held one package per integration, so **adding a library modified it**, and an application installing
one plugin read seven others' dependencies to get it.

And not *inside* the library — but not for the reason it first looks like. `compile-only` would keep
Ktor and Spring off a consumer's runtime classpath perfectly well, so a CLI taking `stx-jpa` would
pay nothing at runtime for an `integration/ktor` package sitting unused in the jar. What it would pay
is elsewhere, and the coupling does not disappear so much as **reverse direction**: instead of three
seams knowing seven libraries, seven libraries would each know three frameworks. The same edges,
now pointing from the things that must stay light to the heavy ones.

- `stx-jpa` would compile against both Spring Boot and Ktor, so a Ktor version bump recompiles all
  seven libraries.
- Its test module would carry a Ktor test host and a Spring context *beside* its Postgres specs. Today those three fail independently; merged, a broken Spring wiring spec fails the JPA
  library's build.
- Its manifest would stop describing what it is.

And the gain is smaller than it looks: a Ktor application using four of these libraries takes five
dependencies today and would take four, because the seam already provides the "one dependency" on the
framework side.

`libs/stx-ktor` and `libs/stx-spring-boot` keep what belongs to no library and is the same for all of
them — for Ktor, the resource-lifecycle idiom (`own`, `publish`, `resource`, `required`); for Spring,
error handling, CORS, security, and the JSON and web conventions. They are the **seam**, not the
switchboard. Those four Ktor verbs are public rather than internal for exactly this reason: they are
the contract between the seam and every integration built on it, and a contract cannot be internal.

The framework modules know the framework, the libraries know the backends, and neither knows two.

There is no Koin seam. `libs/stx-koin` existed and was removed: these libraries are server-side, so
Ktor's own DI covers the callers that have a framework, and Koin interoperates with it for the ones
that prefer it. What the module actually held was one line per backend —
`single { Redis.connect(config) } onClose { it?.close() }` — which an application writes itself in
less time than it takes to find the dependency.

**Which side of the line an integration falls on is not its size — it is whether it has a design of
its own.**

- A module, when it brings its own lifecycle, its own configuration surface, its own decisions:
  `stx-workflow-spring` chooses between three stores and runs a `SmartLifecycle`;
  `stx-graphix-ktor` owns routes, a websocket protocol and a sandbox. Each needs a README to explain
  a decision, and that is the tell.
- In the seam, when it is **the seam's idiom applied to one more type**: `stx-ktor/redis` is
  `resource(RedisKey, instance) { Redis.connect(config) }` and an accessor. Its README would read
  "the idiom, applied", and a published artifact each — manifest, README, coordinates for a consumer
  to discover — costs more than it removes.

So `workflow` and `graphix` are modules and the seven small ones stay where they are. The line was
drawn after moving `workflow` and reading the others; the first version of this rule said every
integration should move, which would have meant a module per library per seam for code that mostly
restates one idiom once each.

A *new* integration is written to this rule rather than added to a hub by default: ask which of the
two it is first.

**Every backend is declared `compile-only`, including the ones this module's API returns.**
`call.redis` hands back a `Redis` and Lettuce still stays off a consumer's runtime classpath, which
sounds wrong and is not: an application that installs `RedisConnection` already depends on
`stx-redis`, because `RedisConfig` is the only way to configure the plugin at all — and one that
installs only `I18n` never loads a class from any of the others, so nothing is missing when
nothing is linked. It is self-enforcing rather than a convention to remember. Verified with
`./kotlin show dependencies -m stx-ktor`: a compile-only entry sits in the COMPILE scope and is
absent from RUNTIME.

The tests are the other half: they need the real libraries at runtime, so `test-dependencies`
carries each of them again at normal scope, plus `//libs/stx-testing` for the servers to talk to.
Every plugin is specced against a real backend, because "one connection, closed on stop" is not
observable from a mock.

The library knows the backend, the integration module knows the framework, and neither has to know
both. That is the whole of the rule, and it is why an integration is a module beside its library
rather than a package inside it.

**A framework integration must assume the resource is not its own, and must not be the only way to
reach it.** Three rules, and the next integration is built to them rather than retrofitted:

- **Take an instance as well as a config.** Every plugin's configuration has an `instance`; set it
  and the plugin adopts what an application or a container already built, instead of opening a
  second one.
- **Whoever created it closes it.** `Resources.kt` says this once, as `own` (we opened it, we close
  it on `ApplicationStopped`) and `publish` (someone else's, we leave it alone). A plugin that
  adopts a connection and also closes it is the second close.
- **Reaching a resource only through `call.x` is a service locator.** A class a container builds
  has no `ApplicationCall`, so each plugin can register what it installed —
  `install(RedisConnection) { config = …; injectable = true }` — and the same connection is then
  both `call.redis` and a constructor parameter. `injectable` is off by default because
  `ktor-server-di` is compile-only, and each `provideX` lives in its own file so nothing loads a
  class from Ktor's DI until it is switched on.

**And close idempotently, through `CloseGuard`.** A resource that is handed around is closed more
than once, and the rule above says who *should* close it, not what happens when two of them do.
Ktor's DI closes every `AutoCloseable` it hands out at application stop — one a provider merely
passed through included, and a per-key `cleanup` runs beside that hook rather than instead of it, so
a library cannot opt out. The drivers do not agree here either: Lettuce and the MinIO client tolerate
a second close, the RabbitMQ client throws. Any new `AutoCloseable` in these libraries closes through
the guard, so that all of it stays a question of tidiness rather than of correctness.

`libs/stx-spring-boot` is the same seam for Spring that `stx-ktor` is for Ktor, and it follows the
same `compile-only` rule for the same reason. Two things about it are specific to this toolchain and
neither is guessable:

- **Nothing it registers is on until a property asks for it.** Every bean is
  `@ConditionalOnProperty(prefix = "stx.<name>", name = ["enabled"], havingValue = "true")` with **no
  `matchIfMissing`**, and every one is `@ConditionalOnMissingBean` so an application's own bean wins.
  Putting the module on a classpath starts nothing — which is what makes it safe for an application
  that already has an exception handler of its own.
- **The IDE metadata is written by hand, and a spec keeps it honest.**
  `spring-boot-configuration-processor` is a *Java* annotation processor; the toolchain has no kapt,
  and `settings.java.annotationProcessing` runs javac over Java sources only — so the processor never
  sees a Kotlin `@ConfigurationProperties` class and generates nothing, silently. The keys therefore
  live in `resources/META-INF/additional-spring-configuration-metadata.json`, Spring Boot's own
  supported manual file, and `ConfigurationMetadataTest` scans the module for
  `@ConfigurationProperties` classes and fails when a property has no entry or an entry has no
  property. **A new `stx.*` key is added to that file in the change that reads it** — and to
  `docs/spring-configuration.md`, which is where a reader looks. Do not reach for the processor; it
  will appear to be configured and produce nothing.

One more thing that only a test says out loud: `compile-only` keeps a dependency off the *test*
runtime too, so a `@ConditionalOnClass` guarding it correctly declines to match in a spec. Add the
dependency to `test-dependencies` at normal scope — the same shape `stx-ktor` uses — rather than
weakening the condition.

### Local services

The databases this workspace runs against are **already containerised and usually already up** —
`~/workspace/docker/apps/` holds one compose file per service: `database/mongo` is an `rs0` replica
set on `localhost:27017`, transactions included, `database/redis` is Redis Stack on
`localhost:6379`, `minio` is an S3-compatible store on `localhost:9000`, `kafka` is a three-broker
KRaft cluster, and `rabbitmq` is on `localhost:5672` with its management UI on `15672`. Check `docker ps` before pulling an image or starting a Testcontainers container:
the pull costs a gigabyte and the second container either clashes on the port or silently tests a
different server than the one everything else uses.

**A spec must not depend on the host having the right daemon up.** `libs/stx-testing` declares
each backing service and resolves it in one order: the environment variable if it names a server,
otherwise a container started once for the run, otherwise `available == false` and the spec skips.
Mongo, Redis, AMQP and MinIO all work this way. Declare a new backend in `Backends.kt`, never in a
library's own test tree.

**A harness reaches the endpoint with `requireEndpoint()` and names its namespace with
`TestNames`.** Both live in `stx-testing` and both replaced a pattern that was written out eleven
times. `endpoint!!` past an `available` gate throws a `NullPointerException` naming a line, where
`requireEndpoint()` throws `describe()` — which of the three resolutions this was, and why. And
because a container is started once and *shared*, a spec's isolation is a database, schema, topic,
bucket or key prefix of its own inside it: `TestNames("stx_mongo_test", separator = "_").next()`
carries a per-run suffix, so a crashed run's leftovers cannot collide with the next run's names. The
thing it replaced was a prefix *sweep* before the first spec, which could not tell a crashed run's
databases from a concurrent run's and so had two suites deleting each other's data. Never reintroduce
a sweep; name what cannot collide. `libs/stx-testing/README.md` has both.

**A Spring application gets its MongoDB as a bean, not as a property.** `stx-spring-boot`'s
`com.softistx.spring.testing` ships `MongoSpec` — `@SpringBootTest` plus a `MongoConnectionDetails`
contributed over `stx-testing` — so no application writes a `@DynamicPropertySource` of its own. That
is not only about repetition: a property name can be wrong and say nothing, and one was. Boot 4 moved
the driver's settings from `spring.data.mongodb` to **`spring.mongodb`**, and `examples/spring-orders`
spent a phase talking to `mongodb://localhost/test` — the workspace's own replica set — with a
container running beside it and a green suite. A bean is asked for by type and cannot be misspelled.
`libs/stx-spring-boot/README.md` has the four lines an application writes.

`spring.mongodb.database` there names a *prefix*, not a database: the test support appends the run
suffix — the `TestNames` rule above, reached through a `BeanPostProcessor` because
`reactiveMongoDatabaseFactory` reads the property before the connection string — and drops the result
at JVM exit. Two suites against the workspace replica set therefore cannot clear each other's
collections, and a run leaves it as it found it.

| library | override | without it |
| --- | --- | --- |
| `stx-mongo` | `MONGO_TEST_URI` | `mongo:8`, a single-node replica set |
| `stx-redis` | `REDIS_TEST_URI` | `redis:8-alpine`, on db 15 |
| `stx-amqp` | `AMQP_TEST_URI` | `rabbitmq:4-management` |
| `stx-storage` | `MINIO_TEST_ACCESS_KEY` **and** `..._SECRET_KEY` | `minio/minio:latest` |
| `stx-kafka` | `KAFKA_TEST_BOOTSTRAP` | `confluentinc/cp-kafka:latest`, one broker |
| `stx-jpa` | `POSTGRES_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `postgres:18-alpine` |
| `stx-jpa` | `MYSQL_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | `mysql:8.4` |
| `stx-jpa` | `DB2_TEST_URI` **and** `..._USER` **and** `..._PASSWORD` | nothing — the DB2 specs skip |

**The credentials rule is unchanged; what it costs is not.** `AMQP_TEST_URI` and the MinIO key pair
still have no defaults and must never gain any — a credential with a default is a credential in
source control, and they live in `~/workspace/docker/apps/*/.env`, exported for a run and never
committed. But their absence is no longer a reason to skip: a container hands out credentials of its
own, so the 78 specs in those two libraries now run on a machine where nobody exported anything.
They used to report skipped there and prove nothing.

`MINIO_TEST_ENDPOINT` on its own does not take the override — an endpoint with no way in fails later
and less clearly than a container would. `POSTGRES_TEST_URI` is refused on its own for the same
reason: a Postgres URI does not carry the password, and a driver that connects without one fails at
authentication in a way that reads like a network problem.

**Each `stx-jpa` spec gets a schema of its own**, created before it and dropped `cascade` after
it — the per-spec Mongo database and Redis namespace, in the shape Postgres has for it. It earns its
keep against a real server: a spec creating its tables in `public` would be working among whatever
else lives there, and Hibernate's `create-drop` would take that with it on the way out. The workspace
runs `postgis/postgis:latest` on 5432, which is exactly such a server.

The reuse path is still the fast local loop, and still the seam CI uses to point at a service it
provisioned. A reused server is shared, so everything below about leaving it as you found it applies
to it exactly as before.

**Kafka's container is one broker, and that costs something worth knowing.** The workspace cluster
is three brokers with `min.insync.replicas = 2`, so a topic there has three replicas and
`acks = all` really waits for a quorum; a container gives one replica, so it waits for one broker.
The ack path is exercised either way, the quorum only on the real cluster. Nothing asks for a hard
three any more — `KafkaTestCluster.replicationFactor` asks the cluster what it has, capped at three,
because a topic asking for more replicas than there are brokers is not a weaker test but a refused
`createTopics`.

To exercise the quorum, point at the workspace cluster. Its brokers advertise container hostnames
and publish no host ports, so the names have to resolve first — an address the host can reach is not
enough on its own:

```
# /etc/hosts
172.22.0.115 kafka1
172.22.0.116 kafka2
172.22.0.117 kafka3
```

```bash
KAFKA_TEST_BOOTSTRAP="kafka1:9092,kafka2:9094,kafka3:9096" ./kotlin test -m stx-kafka
```

**An override that does not answer is not quietly replaced by a container.** Naming a cluster and
getting a container instead would be worse than skipping: the run would look green and would have
tested something else. This holds for all five backends.

To take the override and run against the workspace's own broker or object store:

```bash
set -a; . ~/workspace/docker/apps/rabbitmq/.env; set +a
AMQP_TEST_URI="amqp://$RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS@localhost:5672/%2F" ./kotlin test -m stx-amqp

set -a; . ~/workspace/docker/apps/minio/.env; set +a
MINIO_TEST_ACCESS_KEY=$MINIO_ROOT_USER MINIO_TEST_SECRET_KEY=$MINIO_ROOT_PASSWORD ./kotlin test -m stx-storage
```

The `%2F` there is the default virtual host and not decoration — a plain trailing `/` is the *empty*
vhost, which the broker refuses with a message about permissions that says nothing about the cause.
The container's URI carries no vhost path at all and sidesteps it.

A run that reuses a server has to leave it as it found it, because it is not theirs: the Mongo specs use a
database per spec and drop it, the Redis specs use database 15 with a key namespace per spec and
delete it, the storage specs create a bucket per scenario and empty and remove it, and the Kafka
specs create only the topics and groups they delete, and the AMQP specs name every exchange and
queue uniquely per run and delete them. Nothing here calls `FLUSHDB`, and nothing touches a bucket,
a topic or a queue it did not create.

Keep all of that even where a container made it unnecessary. Against a container, a spec that fails
to clean up after itself is a spec whose next run behaves differently — the isolation is what makes
that visible, and `FLUSHDB` is what would hide it.

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

**Local modules come first in every dependency list**, before any `$libs.*` or `$spring.*` alias.
A module's own composition — what it is built out of — reads before what it borrows from the
outside, and a `//libs/` entry buried between two catalog aliases is the one a reader misses when
asking what a module actually depends on.

Modules are registered in `project.yaml`, and this repo registers them **by glob**, so a new module under `libs/`, `libs/<group>/`, `plugins/`, `examples/<name>/` or `examples/<group>/<name>/` is picked up without editing the file:

```yaml
modules:
  - examples/*
  - examples/*/*
  - libs/*
  - libs/*/*
  - plugins/*
```

Only directories that directly contain a `module.yaml` are matched, so grouping directories such as `examples/material-demo` and `libs/stx-graphix` and every `src/`, `test/` and `build/` are ignored. Two ways a glob goes wrong: `**` is rejected — express depth with successive `*` segments, which is why `examples/*` and `examples/*/*` are both listed — and a pattern matching *nothing* is reported as an error, so don't add a line for a directory that doesn't exist yet. There is no nesting: one `project.yaml` defines the project root, it has no include directive, and module dependencies may not cross a project boundary.

Rules that are easy to get wrong:

- **A module's name is its directory name, and it must be unique across the whole project.** There is no `name:` property in `module.yaml` (it fails with `Unknown property`), and a `modules:` entry is a path string, not a mapping — so two directories called `android` under different parents abort *every* command with `Module name 'android' is not unique`. `-m` takes the bare name only, never a path, so there is no way to disambiguate after the fact. Hence `md-catalog`/`md-desktop`/`md-android` rather than `catalog`/`desktop`/`android`: prefix a demo's modules so the next demo can have the same shapes. `description:` gives a module a readable label in `kotlin show modules`, but does not change its name.
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

**A module that turns on `settings.ktor` pins the version to the catalog's.** `ktor: enabled` gives
`$ktor.server.core` and the rest from the toolchain's *own* default version, which is 3.5.2 today
and matches `ktor = "3.5.2"` in the catalog by coincidence rather than by construction — a toolchain
upgrade would move one and not the other, and an artifact this repo names itself (`ktor-server-di`,
on `version.ref = "ktor"`) would then be a different Ktor from `ktor-server-core`. So:

```yaml
settings:
  ktor:
    enabled: true
    version: 3.5.2   # matches `ktor` in libs.versions.toml
```

`./kotlin show settings -m <module>` says which one is in force: `# module.yaml` when it is pinned,
`# default` when the toolchain is choosing. Three modules enable it — `stx-ktor`, `demo-api`,
`demo-client` — and all three carry the pin.

The catalog's `kotlin = "2.4.0"` entry is for consumers that need an explicit Kotlin version; the toolchain supplies its own compiler and stdlib (2.4.10 with CLI 0.12.0), so that entry does not control what this repo compiles with.

## Coroutine-first Kotlin

Every library here wraps a blocking, callback-driven Java client. The shape that keeps working is
the same each time, and the mistakes are the same each time too.

- **Blocking calls go on `Dispatchers.IO`, and everything in these clients blocks.** A declare, a
  poll, a send, a `basicPublish` — each is a round trip however small it looks. A blocking call left
  on a caller's dispatcher is a thread the rest of the application needed.
- **Prove the client's threading rule before designing around it.** Two of these libraries were
  built on a guess that turned out to be wrong. `KafkaConsumer` was assumed to be thread-*affine*
  and was given a dedicated thread; `ConsumerConfinementTest` showed it wants *exclusion during a
  call*, so `Dispatchers.IO.limitedParallelism(1)` does the job and the thread went. `AmqpPublisher`
  carried a `@Volatile` between two callbacks; `ConfirmThreadsTest` showed both arrive on one
  connection thread, so it was insuring against nothing. Both specs need no server and run in
  milliseconds. Write the spec, then design.
- **A callback that cannot suspend gets a `Mailbox`, never `runBlocking`.** There is no other
  correct bridge: a `Mutex` may suspend and a callback may not, and `runBlocking` on a client's own
  I/O thread parks the thread it needs for heartbeats and deliveries. See the shared code section
  for which of the three concurrency types fits which caller.
- **Prefer a single owner to a lock.** One coroutine draining a mailbox owns everything the messages
  touch, so the state under it is plain `var`s and plain maps — no `@Volatile`, no concurrent
  collection. It also buys what no pair of guarded flags can: two facts that must be applied in
  order *are*, because a channel is FIFO. Reach for a concurrent collection only after establishing
  that a single owner will not do.
- **Never hold a lock across suspending work.** A mutex held while a loader runs turns *n*
  concurrent loads of *n* different keys into one queue. `CoroutineSafeMap.getOrPut` takes a value
  rather than a loader for exactly this reason; `KeyedMutex` is the type for when the work suspends.
- **When a foreign API forces a real thread, make it virtual.** Coroutines first, as everywhere
  else here — but `Runtime.addShutdownHook` takes a `Thread` and there is nothing to negotiate.
  Then it is `Thread.ofVirtual()`, never `Thread(…)`: a few hundred bytes against a megabyte of
  committed stack, and blocking parks a continuation instead of an OS thread. On the JDK 25 this
  repo runs, JEP 491 removed the `synchronized` pinning that used to be the argument against them.
  Mind which half of the API you take — `Thread.startVirtualThread` starts on the spot, so a hook
  built with it is registered already-dead (never runs) or refused as still-alive (and then the
  whole holder fails to initialise), and **both outcomes are swallowed without a word**. The form
  that works is `Thread.ofVirtual().unstarted { … }`. `ContainerService` carries the scar and the
  spec that would have caught it.
- **A loop that never suspends starves its own dispatcher.** `KafkaSubscriber`'s poll loop owns its
  dispatcher for the whole poll timeout and has no suspension point between turns, so anything that
  tried to `withContext(thatDispatcher)` waited forever — a real deadlock, found by a spec that hung
  for ten minutes. Work reaches such a loop as a message it applies on its next turn, never as a
  call that waits to be scheduled.

## Performance

- **One client per configuration, not per call.** A Lettuce connection multiplexes and is
  thread-safe, a `KafkaProducer` batches across callers and holds connections to the whole cluster,
  and an AMQP connection carries any number of channels on one socket. Two of any of them halves the
  batching and doubles the sockets. What *does* need one each is the thing whose state is
  per-conversation: an AMQP channel per publisher and per consumer, because delivery tags and
  confirm sequence numbers mean nothing anywhere else.
- **Batch by enqueueing in order and awaiting together.** `sendAll` and `publishAll` hand the whole
  collection to the client and then await the acknowledgements, rather than launching a coroutine
  per record. One coroutine each gives up the ordering these APIs promise and buys nothing, since
  the client batches either way — this was a bug before it was a rule.
- **Backpressure is a default, not an option.** Both consumers bound what may sit between the broker
  and the handler — `SubscriberOptions.prefetch` at 64, `ConsumerOptions.prefetch` at 32 — because
  the protocols' own default is *everything*: one consumer holding a queue's worth of messages in
  memory while its peers hold none. In Kafka the loop also keeps polling while paused, since not
  polling is what gets a consumer evicted mid-batch.
- **Say what a query loads. Entity associations are `LAZY`, and what a caller needs is fetched.**
  Hibernate Reactive has no transparent lazy loading — there is no thread to block on the second
  select — so an unfetched `LAZY` association throws when it is read, inside the session as readily
  as after it, and the tempting fix of leaving associations `EAGER` is the N+1 wearing a different
  hat: three rows pointing at three different owners cost three secondary fetches with JPA's
  `@ManyToOne` default and none with `fetch(…)`. `FetchJoinTest` counts both off Hibernate's own
  `entityFetchCount`, which is the counter to reach for — `prepareStatementCount` reads zero,
  because there is no JDBC under the Vert.x pool. So: annotate every association `LAZY`, name what
  the query needs with `fetch` / `fetchEach`, and where the caller only reads a few columns, project
  instead and load no entity at all — Hibernate packages any result class with a matching
  constructor, so `query<Summary>("select a, b from …")` needs no constructor expression. Where there
  is no query to join on — `find` and a stateless `get` —
  the answer is an entity graph:
  `session.entityGraph<Purchase>().add(…)` is a fetch plan that is also a value, so a `find` and a
  query cannot disagree about what they load. `docs/jpa-criteria.md` has the rules for both,
  including the one nothing enforces: `limit` and `offset` silently truncate whenever a query loads a
  collection, whether by `fetchEach` or by a graph naming one.
- **Keep the fast tests fast and the slow ones optional.** Timing claims — a full buffer pausing, a
  strategy committing when it says it does, partitions running concurrently — belong on Kafka's own
  `MockConsumer`/`MockProducer` and run in about two seconds. A real server is for behaviour that
  *is* the server's: an acknowledgement removing a message, a TTL expiring one. Those specs skip
  when the server is unreachable, so a machine without it reports skipped rather than red.
- **Watch what the machine is carrying.** The workspace's containers do not all fit at once: the
  three-broker Kafka cluster is around 1.9 GiB, and starting it alongside everything else once drove
  this machine into the OOM killer, which chose the running IDE. Check `docker ps` first, stop what
  you started, and prefer the specs that need nothing running.

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
  | `libs/stx-openapi-generator/README.md` | How is the module shaped, what does each emitter produce, how do I add one? Roughly constant in size. |
  | `plugins/openapi/README.md` | How do I turn this on in a module, and what does that need on its classpath? |
  | `plugins/dgs-codegen/README.md` | How do I generate DGS types from SDL in a toolchain module |
  | `plugins/apollo/README.md` | How do I generate Apollo models and `OPERATION_DOCUMENT` from schema + documents |
  | `libs/stx-common/README.md` | What belongs in the shared module, which concurrency type a given caller wants, the `getOrPut` trap, and the table of what the standard library already covers so nothing here wraps it twice |
  | `libs/stx-amqp/README.md` | The same, for AMQP — topology, confirms, prefetch, and why a retry is a queue nobody consumes |
  | `libs/stx-i18n/README.md` | The same, for i18n — the locale walk, what eager compilation buys, and why `ResourceBundle` is not underneath it |
  | `libs/stx-ktor/README.md` | The Ktor integrations — what each plugin owns and closes, and how one module holds them all without becoming a fat dependency |
  | `libs/stx-jpa/README.md` | The same, for Postgres — the confinement rule the library is built around, and why entities need two compiler plugins. Roughly constant in size |
  | `docs/jpa-criteria.md` | What a stx-jpa query may say — the operators, joins, fetch joins, entity graphs, projections, function vocabulary and the two escapes. **This is where a new operator or function is documented** |
  | `docs/jpa-mapping.md` | What a stx-jpa entity may say — the database, column naming, identifiers, `Instant`/`Uuid`, JSON columns, validation. **This is where a new `SqlTypes` code, strategy or converter is documented** |
  | `docs/graphix.md` | What a stx-graphix schema may say — the annotations, scalars, field directives, DataLoaders, what a resolver may see (instance, `@Argument`, `@GraphQLContext`). **This is where a new annotation, scalar or directive is documented** |
  | `libs/stx-graphix/stx-graphix/README.md` | How the GraphQL engine is shaped, why SerialDescriptor and not Jackson, why there is no class scan in core |
  | `libs/stx-graphix/stx-graphix-ktor/README.md` | The Ktor plugin — path, `instance` vs `schema { }`, `fromDi`, `injectable` |
  | `libs/stx-graphix/stx-graphix-spring/README.md` | The Spring Boot plugin — `stx.graphix.enabled`, `@GraphQLController` scan |
  | `docs/telemetry.md` | What a stx-telemetry call may say — the root's settings, every log and span verb, the severities, the attribute conversions, `traceparent` and the signal model. **This is where a new verb, severity, span kind or exporter is documented** |
  | `libs/stx-telemetry/stx-telemetry/README.md` | How the telemetry library is shaped — why a coroutine context element and not an MDC, why the thread-local mirror is not a contradiction, why writing a signal never waits |
  | `libs/stx-telemetry/stx-telemetry-otlp/README.md` | The OTLP exporter — why not the Java SDK, the encoding details a receiver is strict about, what is retried and why a partial success is not |
  | `libs/stx-telemetry/stx-telemetry-slf4j/README.md` | The SLF4J bridge — which direction to pick, why reading a third-party MDC is not a contradiction, and why both at once is refused |
  | `libs/stx-telemetry/stx-telemetry-mongo/README.md` | The MongoDB exporter — the document's shape, the dates, and retention as a TTL index |
  | `libs/stx-telemetry/stx-telemetry-ktor/README.md` | The Ktor plugin — why the span wraps the pipeline, why the name is fixed after routing, and why a thrown handler leaves no status code |
  | `libs/stx-telemetry/stx-telemetry-spring/README.md` | The Spring auto-configuration — why `CoWebFilter` and not `WebFilter`, and the 200 WebFlux never sets |
  | `docs/workflow.md` | What a stx-workflow declaration may say — every verb and every annotation, the step scope, the statuses and their transitions, the persisted record, the store contract. **This is where a new verb, annotation, retry policy or status is documented** |
  | `libs/stx-workflow/stx-workflow/README.md` | How the workflow engine is shaped — why checkpointing and not replay, what at-least-once asks of a step, why a fan-out needs an explicit merge |
  | `libs/stx-workflow/stx-workflow-db/README.md` | Where instances live — why one module and not three, and per store: the index, the conditional write, the lease and retention |
  | `libs/stx-kafka/README.md` | The same, for Kafka — the publisher, the poll loop, and why the loop is shaped the way it is |
  | `libs/stx-mongo/README.md` | How is the Mongo library shaped, and why is each non-obvious part the way it is? |
  | `libs/stx-spring-boot/README.md` | The Spring integrations — the opt-in `stx.*` model, why the configuration metadata is hand-written, why the locale comes off the exchange, and the test beans an application's specs are built on |
  | `docs/spring-mongo-queries.md` | What a stx-spring-boot Mongo query may say — the predicate operators, the filter and sort grammars, and the keyset paging rules. **This is where a new operator or filter token is documented** |
  | `docs/spring-configuration.md` | Every `stx.*` key, its default and what enabling it costs. **This is where a new configuration key is documented** |
  | `libs/stx-redis/README.md` | The same, for Redis — including what each layer deliberately does not do |
  | `libs/stx-storage/README.md` | The same, for object storage — and what a presigned URL can and cannot promise |
  | `libs/stx-material/README.md` | How is the UI library shaped, how does `StxTheme` slot into an application that already uses Material 3, and how do I add a component? |
  | `libs/stx-material/docs/tokens.md` | What a token may say — the colour roles, spacing, durations and easings, and why shapes and elevation are M3's. **This is where a new token is documented** |
  | `libs/stx-material/docs/components.md` | Every component, its parameters, and its story in the catalogue. **This is where a new component is documented** |
  | `libs/stx-material/docs/roadmap.md` | Where the library is — the phases and what each delivered. **A box is ticked in the change that delivers it, never after** |
  | `examples/spring-orders/README.md` | What each file in the Spring demo is there to show, how to run it, and what it deliberately leaves out |
  | `examples/graphix-shop/README.md` | What the GraphQL catalogue shows — split SDL, annotated DataFetchers, how to run it |
  | `examples/graphix-codegen/README.md` | The two GraphQL codegen plugins on one schema |
  | `examples/workflow-checkout/README.md` | What the checkout saga shows — the journal each run leaves, and the window between an effect and its checkpoint that the idempotency key exists to cover |
  | `examples/material-demo/README.md` | Why the demo is three modules, how to run it, and how a story is registered |
  | `libs/stx-testing/README.md` | Where an integration spec's server comes from, how a container declared there is cleaned up, and the two conventions every harness follows — `requireEndpoint()` and `TestNames` |
  | `AGENTS.md` | How do I work in this repo? One paragraph per capability, never the detail. |

  When a README section starts growing every phase, that is the signal it belongs in `docs/`, not
  the signal to keep appending. `libs/stx-openapi-generator/README.md` reached 394 lines before its
  reference half moved out; splitting on *audience* rather than on length is what made the seam
  obvious. `libs/stx-jpa/README.md` reached 921 and split the same way, into the query vocabulary
  and the mapping vocabulary — the two halves that grow — leaving the reasoning behind.
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
- **Every module's packages start with `com.softistx`.** The rest follows the module: `com.softistx.openapi` for `libs/stx-openapi-generator`, `com.softistx.openapi.plugin` for `plugins/openapi`, `com.softistx.demo.api` for `examples/demo-api`. Generated code follows the same rule — the `openapi` plugin's `packageName` setting is set per module, and defaults to `generated.api` only when nobody sets it.
- **Organise by package, not as a flat pile of files — `test/` exactly as much as `src/`.** A module
  with more than one concern gets a directory per concern, and the directory matches the package —
  `src/parser/` is `com.softistx.openapi.parser`. The root package holds only what every package
  depends on: the shared contract, nothing else. When a file lands in the root because it did not
  obviously belong anywhere, that is the signal a package is missing.

  A test tree is not exempt, and it is where this slips: a fixture gets written beside whichever spec
  needed it first, and three specs later the fixtures are scattered across four packages with no rule
  anyone could state. **Test fixtures live in a package named for what they are, not for the spec that
  happened to need them first** — entities in `test/entity/`, and the same for any other family of
  fixture a module grows. A spec imports its fixtures; it does not host them. `stx-jpa`,
  `stx-ktor` keeps its JPA entities in `…entity`.

  One exception, and it has to be argued in the file: a spec that is *about* a package boundary owns
  the package it scans. `stx-jpa`'s `EntityScanTest` needs a package holding nothing but the
  classes it expects to find, which is why those fixtures sit in `test/entity/scan/` instead of
  beside the rest.
- `.gitignore` excludes `build`, `.idea`, and `.jbeval`; build output goes to `build/` under the project root unless `--build-dir` overrides it.
- **`develop` is where work lands and every PR targets it.** Branch off `develop`, open the
  pull request against `develop`, and merge it there. Nothing is merged directly into `main`, however
  small and however green — a PR opened against `main` has the wrong base and wants recreating, not
  merging.

  One exception, and only one: the slices of a feature too large for a single PR target that
  feature's own integration branch, and that branch is what targets `develop`. See
  `large-feature-branch-workflow`, which owns the shape. Everything else is one branch, one PR,
  base `develop`.
- **`main` is aligned from `develop`, only when that is asked for.** Aligning is not part of finishing
  a feature: it happens when someone asks for it, and it is `git checkout main && git merge develop`
  — never merging a feature branch into `main`, never cherry-picking across. That fast-forwards while
  it can and leaves a `Merge branch 'develop'` commit once it cannot, which is the same shape
  `nxgt-federation` and `sellix-monorepo` carry on their own `main`.

  If `develop` is *behind* `main`, someone has written to `main` directly and that is the thing to fix
  first: merge `main` into `develop`, then align `main` from the result. No history is rewritten
  either way.

  The failure this prevents is quiet: merging features into `main` while `develop` sits behind works
  perfectly until `develop` carries real work of its own, and then the two have genuinely diverged
  with no single branch holding everything. It has already happened once here — three PRs landed on
  `main` while `develop` was nine commits behind, and it was harmless only because `develop`'s three
  extra commits were merges carrying no file changes at all.
