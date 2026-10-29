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
| `libs/api/stx-openapi-generator` | Reads an OpenAPI spec, emits models and a typed client with KotlinPoet |
| `libs/core/stx-common` | What more than one module needs and nothing else. Two concurrency packages, split by whether the caller can suspend: `coroutines/` has `CoroutineSafeMap`, `KeyedMutex`, `Lease` and `Mailbox`; `concurrent/` has `Memo`, `Guarded`, the `ConcurrentMap` extensions and the `BlockingQueue` bridge, for the Hibernate binders and SLF4J initialisers that cannot. Plus `CloseGuard`, `http/CorsPolicy` (the one CORS policy both web seams build from), the keyset-pagination half both stores share, and the one lenient `Json` the storage and messaging libraries read through |
| `libs/messaging/stx-amqp` | AMQP over the RabbitMQ client: topology in one block, publishes that wait for the confirm, deliveries as a `Flow`, and a delay-queue retry path |
| `libs/api/stx-i18n` | Message catalogs compiled once at startup, a per-key walk down the locale chain, ICU arguments and plurals, `Accept-Language` negotiation, and an audit of what each locale is missing |
| `libs/data/stx-jpa` | Postgres for a Kotlin coroutine service, over Hibernate Reactive: annotated Kotlin entities, sessions confined to the event loop that opened them, HQL, SQL and JPA Criteria — named by `KProperty` rather than by strings — through one suspending builder |
| `libs/ui/stx-material` | The repo's one client-side library — Compose Multiplatform components over Material 3: `StxTheme` takes M3's own four inputs and wraps `MaterialExpressiveTheme`, component looks are declared as Compose `Style`s with their interaction states animated, and every curve comes from M3's `MotionScheme` rather than a hand-written `tween` |
| `libs/messaging/stx-kafka` | Kafka for a Kotlin coroutine service: suspending sends, records as a `Flow`, offsets committed after the handler, and an admin client |
| `libs/core/stx-ktor` | The Ktor seam, and only the seam: the resource-lifecycle idiom every plugin is built on (`own`, `publish`, `resource`, `required`), and the one CORS policy `stx-spring-boot` builds Spring's from. No integration lives here — each is a module beside its own library — and the tell is that no spec in this module talks to a backend |
| `libs/data/stx-mongo` | MongoDB for a Kotlin coroutine service: CRUD collection extensions, keyset pagination, an opt-in audit trail, GridFS |
| `libs/data/stx-redis` | Redis for a Kotlin coroutine service, over Lettuce: a namespaced connection owning one `Json`, and kotlinx-serialized cache, lock, topics and streams |
| `libs/core/stx-spring-boot` | The Spring seam, a package per concern: translated errors in one response shape, the request's locale read off the exchange rather than a `ThreadLocal`, security, CORS, the JSON codec, the HTTP-service clients (`client/`: `httpClient`, `httpServiceFactory`, `generatedApiFactory`), and the Spring Data Mongo layer. Each `stx-*` library's own auto-configuration is a module beside that library, not a package here |
| `libs/data/stx-storage` | S3-compatible object storage over the MinIO SDK: buckets, objects, and presigned URLs and upload forms |
| `libs/api/stx-graphix` | GraphQL over graphql-java 26.1: annotated Kotlin functions, `@Serializable` types, suspending execution. `stx-graphix-ktor` and `stx-graphix-spring` are the HTTP integrations, `stx-graphix-koin` builds the schema from a Koin container |
| `libs/messaging/stx-workflow` | Compensable workflows for a Kotlin coroutine service: a DSL of steps each with its own compensation, one `@Serializable` context threaded through them, and state checkpointed after every node so a process that dies mid-run is picked up where it stopped. `await` and `sleep` stop an instance for a signal or a deadline by writing it down rather than by holding a coroutine, and `workflowOf` reads the same declaration off an annotated class. `engine.find(Failed)` is the operator's inbox for the one outcome the engine refuses to resolve. Four modules in the group: the engine, `stx-workflow-db` for where instances live (Redis, SQL through `stx-jpa`, or MongoDB), and `stx-workflow-ktor` / `stx-workflow-spring` for the two framework integrations |
| `libs/data/stx-migrations` | Schema migrations written in Kotlin, on neither Flyway nor Liquibase: a migration is a class with a declared `version`, a ledger records what ran, a renewing lease keeps two processes apart, and the runner is a **gate** — it runs in the process that is about to serve and every way it fails leaves `run()`. `RUNNING` is the status the prior art lacked, and it is what makes a process killed mid-migration fail closed instead of being silently re-applied. Four modules: the core (no store in it, specs run in under a second with no container), `stx-migrations-db` for MongoDB and SQL, and `stx-migrations-ktor` / `stx-migrations-spring` |
| `libs/observability/stx-telemetry` | Logs and traces for a Kotlin coroutine service. The current span and the inherited fields are a `CoroutineContext.Element`, not a `ThreadLocal` — the same argument `RequestTranslator` makes about `LocaleContextHolder`, applied to the tool that is supposed to make such bugs visible. `TelemetryContext` is also a `ThreadContextElement`, so the runtime keeps a thread-local mirror in step and `log.info(…)` needs not suspend. An event is a `@Serializable` type; `Mailbox` is the queue and one coroutine drains it to the exporters |
| `libs/observability/stx-telemetry/stx-telemetry-otlp` | OTLP/HTTP+JSON export for `stx-telemetry`, on `java.net.http.HttpClient` with hand-written `@Serializable` documents. No OpenTelemetry SDK — it would bring its own `ThreadLocal` `Context`. `OtlpExporterTest` pins the document against a JDK `HttpServer`; a second spec gated on `OTLP_TEST_ENDPOINT` asks a real collector, because a stub agrees with whatever we wrote |
| `libs/observability/stx-telemetry/stx-telemetry-slf4j` | The SLF4J bridge for `stx-telemetry`, both ways. Inbound is an `SLF4JServiceProvider` found by SPI, so a third-party library's logs enter the pipeline carrying the current span; it reads the caller's MDC, which is correct because it is read on the very thread that wrote the line and never carried across a hop. Outbound is an `Slf4jExporter` for an application that keeps logback. Both at once is a loop, and the exporter refuses to be built when the bound provider is this module's own |
| `libs/observability/stx-telemetry/stx-telemetry-mongo` | MongoDB export for `stx-telemetry`. A batch is one unordered `insertMany`, and the document is what `signalJson` produces plus the resource, with the three instants written back as BSON dates — JSON has no date, and a string is not something Mongo will expire or index. Retention is a TTL index built on the first batch, because building it suspends and nothing that constructs an exporter does; a changed retention drops and rebuilds it rather than leaving the old window silently in place. It depends on the driver and deliberately not on `stx-mongo`, which is sessions, pagination and GridFS |
| `libs/observability/stx-telemetry/stx-telemetry-ktor` | Ktor plugin for `stx-telemetry`: `install(Observability)`. Unlike every other plugin here it intercepts `ApplicationCallPipeline.Monitoring` rather than using `on(CallSetup)` — a `CoroutineContext.Element` is only in scope inside the `withContext` that installed it, so the span has to wrap `proceed()`. The span is renamed to the matched route on `RoutingRoot.RoutingCallStarted`, which is why `SpanScope.name` is settable |
| `libs/observability/stx-telemetry/stx-telemetry-spring` | Spring Boot auto-configuration for `stx-telemetry`, opt-in behind `stx.telemetry.enabled`. The server span is a `CoWebFilter` and not a `WebFilter`: a `WebFilter` returns a `Mono`, so what it puts in scope lives in the Reactor context, which a suspending `@RestController` method does not read. Needs `kotlinx-coroutines-reactor`, which the WebFlux starter does not bring — found by specs that hung, not by reading a manifest |
| `libs/core/stx-testing` | Test-only support the libraries share: the backing services their integration specs need, reused from the environment or started as containers for the run |
| `plugins/openapi` | Toolchain plugin wrapping the generator as a build task |
| `plugins/dgs-codegen` | Toolchain adapter of Netflix DGS codegen — GraphQL schema to Kotlin types |
| `plugins/apollo` | Toolchain adapter of Apollo Kotlin codegen — schema and documents to Kotlin models |
| `examples/demo-api` | Ktor server implementing a slice of `examples/demo-api/openapi.yaml` |
| `examples/demo-client` | Generates a Ktorfit client from that spec and calls the server |
| `examples/demo-spring-client` | Generates a Spring `@HttpExchange` client from the same spec |
| `examples/jpa-shop` | A Ktor catalogue over Postgres showing `stx-jpa`'s CRUD extensions and audit layer |
| `examples/graphix-shop` | A Ktor GraphQL catalogue showing `stx-graphix-ktor`: split SDL under `resources/graphql/`, annotated DataFetchers, SSE subscriptions |
| `examples/graphix-codegen` | Both GraphQL codegen plugins on one schema: DGS types and an Apollo `OPERATION_DOCUMENT` |
| `examples/spring-orders` | A Spring Boot order book over MongoDB showing `stx-spring-boot` with no configuration class: a spec-first REST API whose controllers implement the generated `@HttpExchange` interfaces, translated failures, keyset paging, an audit trail and two `stx-migrations` migrations run as a startup gate — and `stx-telemetry-spring`, a server span per request with the service's own span nesting inside it and typed events into a collection |
| `examples/workflow-checkout` | A checkout saga over `stx-workflow` and Redis: a step with its compensation, a fan-out whose legs are undone one by one, and a run whose process is killed between the charge and the checkpoint so the idempotency key has something to prove |
| `examples/material-demo` | The `stx-material` catalogue and the School showcase app — Compose Multiplatform, four modules: `md-catalog` holds every story, `md-school` holds the School app, `md-desktop` and `md-android` are launchers for both |
| `.agents/skills/` | Kotlin Toolchain reference + docs-sync skills (see below) |

A module is a directory with a `module.yaml`, registered by path in `project.yaml`.

## The OpenAPI generator

**How a document is authored and laid out is the `openapi-spec-first` skill** — the split under
`<module>/openapi/`, the redocly commands, and what a controller built from the output looks like.
This section is the generator itself.

Two modules and one reference document — read those before changing either module:

- [`docs/openapi-support.md`](docs/openapi-support.md) — what the generator understands of a
  document, and what each part becomes in Kotlin. This is the file that grows.

- [`libs/api/stx-openapi-generator`](libs/api/stx-openapi-generator/README.md) — the generator. swagger-parser reads
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
An operation's `security` stays a list of *alternatives* (OR, each an AND of schemes) all the way to
the client, which sends the first alternative it holds every credential for, none when `{}` is
allowed, and otherwise throws before sending; `docs/openapi-support.md` has the rules.

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
- **`compose-multiplatform`** — the UI stack behind `libs/ui/stx-material`: which platforms a Compose library may declare, what a non-Apple host does and does not verify, the real `$compose.*` catalog keys, and how kotest runs from a common `test/` tree. `references/` caches 67 pages of the official documentation.
- **`material3-compose`** — the Material 3 API surface that actually compiles here. Its `references/` are *not* fetched: `$compose.material3` resolves to its own alpha version line, so the pages are generated from the resolved jar by `.agents/skills/material3-compose/scripts/extract_api.py`.
- **`skill-from-docs`** — builds and refreshes docs-backed skills. Each such skill declares its source in a `docs-source.json`; refresh one with:
  ```bash
  python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill <name>
  ```
  Run it after a version bump, or whenever a cached page disagrees with the tool. Files under `references/` are generated — fix the script, not the output.
- **`openapi-spec-first`** — how a REST API is authored here: a Redocly-split OpenAPI document under `<module>/openapi/`, the file and naming conventions the generator reads, and the repository → service → controller layering over the generated `@HttpExchange` interface. Read it before adding or changing an endpoint. Its `references/` are written by hand rather than fetched — `document-layout.md` for the split document and the redocly config, `spring-api.md` for the five files a Spring API is made of — and they stay in step with `examples/spring-orders`, which is the same thing running.
- **`large-feature-branch-workflow`** — how to split work too large for a single PR into slices that each land on one integration branch, which then lands on `develop` as the feature.
- **`stx-material-components`** — the order of work for a `libs/ui/stx-material` component: place it, check M3, wrap through `*Defaults`, dress it with a `Style`, reach for Expressive, meet the quality bar, prove it with a pixel and a semantics spec, story, docs and changeset. `references/` holds the verified Expressive vocabulary, the visual-quality bar, a worked example and the pre-PR checklist (written by hand).
- **`compose-accessibility`** — semantics, roles, 48 dp targets, contrast, font scale, keyboard focus and reduced motion on Android, iOS and desktop, and how to test them. `references/` caches 14 pages of the Android and JetBrains documentation.
- **`compose-motion`** — spatial against effects specs, `StxMotion`, Style `animate`, shape morph, enter and exit, stagger, reduced motion and what not to animate. `references/` caches 10 pages of the Android animation documentation.

Four come from Google's [`android/skills`](https://github.com/android/skills) catalogue rather than being written here. They describe **Jetpack Compose (`androidx.compose.*`)**, and `libs/ui/stx-material` builds on **Compose Multiplatform (`org.jetbrains.compose.*`)** — an API named in one of them may not exist in the version that compiles here, so check it against `material3-compose`'s `references/components.md` before using it:

- **`styles`** — the Compose Styles API. **This is the default pattern for every component in `libs/ui/stx-material`**, not background reading; see *Styling a component* below.
- **`adaptive`** — window sizes, pointer and keyboard input, multi-pane layouts.
- **`edge-to-edge`** — drawing behind the system bars, for the demo's Android launcher.
- **`jetpack-compose-performance`** — recomposition, stability, lazy lists and deferred state reads. What does not apply on Compose Multiplatform (baseline profiles, Android-only tooling, `computedStateOf`) is in `compose-multiplatform`'s `references/performance-notes.md`; the skill itself stays byte-identical to upstream.

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
press. `libs/ui/stx-material/test@jvm/` holds the rendering specs (eleven `*RenderTest` files, one per group of components, and a few single-component specs beside them); the two that were written
because something looked right and was not are a hover state that was invisible on *selected* chips
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

- **Its look is a `Style`, in its own file** — `components/button/ButtonStyles.kt` holds an internal `buttonStyle` and
  `buttonColors(variant, color)`, and `components/button/Button.kt` holds no colours at all. A style reaches theme tokens through the
  `StyleScope` extensions in `foundation/style/StyleTokens.kt` (`colors`, `scheme`, `shapes`, `spacing`,
  `motion`), which read the `CompositionLocal`s at resolve time rather than closing over whatever
  was in scope when the style was built.
- **Interaction states are declared, not wired.** `pressed { }`, `hovered { }`, `focused { }`,
  `disabled { }` sit inside the style, and `animate { }` inside those makes the transitions free.
  This replaces `animateColorAsState`/`animateFloatAsState` at the call site — a `Modifier.pressScale`
  helper was written here and deleted the same day the API landed, because the style block does it
  better and in one place.
- **The signature carries no styling parameters.** No `backgroundColor`, no `shape`, no
  `contentPadding`. Instead one `style: Style = Style` parameter, defaulting to exactly `Style`
  (the companion, which is the empty style), the last parameter (or the last before a trailing lambda), and applied *last* so a caller's override wins:
  `Modifier.styledBy(style, enabled, base, interactionSource)`.
- **Presentation state belongs to the component.** `styledBy` (or `pressable` for a part that is a
  press target only when clickable, with `pressBase` for the base alone) remembers the style state
  and gives pressed, hovered and focused for nothing; the caller passes business state and never
  remembers a boolean for a visual. Nothing writes `rememberUpdatedStyleState` + `styleable` inline,
  and the 0.99 and 0.97 presses are the shared `rowPress` and `compactPress`.
- **Every curve comes from Material 3's `MotionScheme`.** `StxMotion` holds the scheme
  `StxTheme` installed, and offers M3's two axes — `spatial(speed)` for anything that moves,
  which is a spring and may overshoot, and `effects(speed)` for colour and alpha, which must land
  exactly. Nothing writes `tween(300)` or names an easing. Giving a fade and a slide one curve is
  the mistake this replaced: the combined transition finishes in two stages. It is *held* rather
  than read from the composition because a `Style` block runs at apply time, not in a composable
  scope.
- **Every default has a name in `StxStyles`**, reached as `StxTheme.styles.card`.
  It is a plain `object` behind an extension property, not a `CompositionLocal` — a `Style` reads
  its tokens when it is applied, not when it is written — and it lives in `src/components/StxStyles.kt`
  (`com.softistx.material.components`) so `foundation` keeps knowing nothing about the components. The `xStyle` vals
  behind it are `internal`: `StxStyles.x` is the only public name. Restating a default before editing it is what stops a
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

Skills are budgeted: a `description` is in context every session (keep it ≤250 chars), a SKILL.md body loads on activation (≤~120 lines), and `references/` pages load only when opened. Skills installed from Google's catalogue are exempt from the body budget, because they stay byte-identical to upstream. Put cost in the deepest tier that can hold it.

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
                                           # run this in every change that touches libs/
./kotlin clean                      # drop build/ and project caches
```

## Publishing

Every module under `libs/` publishes as `io.github.softistx:<module-name>:<version>` to Maven Central.
[`docs/releasing.md`](docs/releasing.md) owns the whole circuit; what an agent has to do is:

- **A change under `libs/` carries a changeset naming the family it releases** (`bun changeset`,
  or `bun changeset --empty` when nothing published changes). A `major` also names its dependents.
- **Never edit a version by hand** — not a family's `version:` in its `<family>.module-template.yaml`,
  not its key in `[versions]` of `libs.versions.toml`. `scripts/sync-version.ts` writes both from the
  family's `package.json`.
- **A new `module.yaml` under `libs/` applies its family's template**, never
  `//publishing.module-template.yaml` directly, and a new `//libs/...` dependency across families is
  also an edit to that family's `package.json` (`bun scripts/graph.ts --check`).
- **Publishing runs from the "Version Packages" PR**: merge it and the Release workflow publishes what
  moved, tags each family `<family>@<version>`, and cuts one GitHub release. Nobody publishes by hand,
  and the Portal's `manual` mode means a human releases the upload.
- **`main` is fast-forwarded to `develop`** after the merge; a release is never cut from `main`.
- **Maven Central and signing stay out of the committed template** (`scripts/enable-central.ts` adds
  them in the release job), so the `publish mavenLocal` every contributor runs keeps working.
- `scripts/` is TypeScript run by bun: `bun run typecheck` as well as `bun test scripts/`.
- The publish-to-`mavenLocal` commands, `scripts/modules.ts`, and the things about `kotlin publish`
  that are not guessable (no `-m` fails, the publish cache, `- bom:` entries) are in
  `docs/releasing.md`; the mavenLocal step each change owes is below.

### The examples and the servers consume published artifacts, not modules

**No module under `examples/` or `server/` may name a `//libs/...` dependency.** Each applies
`//stx-artifacts.module-template.yaml`, which adds `mavenLocal` on top of the default repositories,
and names each library through a `$libs.stx.*` catalog alias — `io.github.softistx:stx-jpa:<version>` and not
`//libs/data/stx-jpa/stx-jpa`.

That is the whole point of having examples. A module reference proves the sources compile together,
which the libraries' own specs already prove. A **published coordinate** proves the thing no other
spec here can: that the POM names what a consumer needs, at the scope a consumer needs it. Both
times a published artifact was wrong in this repo, an example is what found it —
`kotlinx-coroutines-reactor` had to become `exported` because `spring-orders` could not compile an
inline function without it, and the split of the two hubs into per-library integration modules left
`stx-spring-boot` exporting `Messages` the *type* while the auto-configuration that builds one had
moved to `io.github.softistx:stx-i18n-spring`, so `spring-orders` started failing its context refresh with
*"required a bean of type 'com.softistx.i18n.Messages' that could not be found"*. On module
references that defect is invisible, because a module reference carries the whole `libs/` graph.

**The cost is a step in the loop: publish to `mavenLocal` in the same change that touches a
library.** Not before a release, not when a consumer happens to fail — every change under `libs/`,
as soon as its own specs are green.

```bash
./kotlin publish mavenLocal -m <library> --non-transitive
./kotlin build -m <example-or-server>
```

Do it even when no consumer is being built in this session. `~/.m2` is what the IDE resolves, what
the next `./kotlin run -m oauth` reads, and what every other checkout on the same machine sees — so
skipping the publish leaves everyone but this session looking at the previous version of a library
that has already been merged. That gap is silent: the build stays green against the stale jar and
reports a signature nobody has written for hours.

A green `./kotlin build -m <consumer>` says nothing about the change unless the publish preceded it.
Saying "the example still builds" after editing a library and not publishing is reporting a stale
result as a verification; state which of the two you actually ran.

A stale artifact is exactly the failure this arrangement exists to expose, and it will expose it —
as a compile error against code you just wrote. Read the two publish-cache notes above before
concluding the library is at fault.

Running a single test:

```bash
# A filter needs -m: without it the CLI refuses ("required to use --include-module").
./kotlin test -m my-module --include-classes 'com.example.MyTest'       # one class: exact FQN, repeatable
./kotlin test -m my-module --include-test com.example.MyTest.myTestMethod   # one method; a bare class name finds nothing
# Do not use a wildcard in --include-classes: here it did not narrow, even a pattern matching
# nothing ran the whole module. Name each class in full.
```

Inspecting the resolved project model — cheap, and it catches manifest errors without a compile:

```bash
./kotlin show modules                    # module names accepted by -m
./kotlin show settings -m <module>       # effective config after templates merge
./kotlin show dependencies -m <module>   # proves a dependency actually resolves
./kotlin show tasks
```

### Toolchain wrapper

`./kotlin` and `kotlin.bat` are committed wrappers pinning the toolchain to the `kotlin_cli_version` at the top of the script (0.13.0). **Use `./kotlin <command>`, not a bare `kotlin`**, so everyone builds with the same version regardless of what is on `PATH`. Regenerate with `kotlin update -c` (add `--target-version=<v>` to move the pin).

**On macOS, `./kotlin build` needs the full Xcode selected, not the Command Line Tools.**
`stx-material` declares two Apple targets, and on a Mac whose `xcode-select -p` answers
`/Library/Developer/CommandLineTools` the build fails even with `Xcode.app` installed. Measured on
both versions: 0.12.2 compiled the iOS klibs and then failed linking the iOS test binary, on
`xcrun: unable to find utility "xcodebuild"`; 0.13.0 checks first and fails in the
`xcodeEnvironment` task with *"Xcode installation is not detected"*. Either
`sudo xcode-select --switch /Applications/Xcode.app` once, or prefix the command with
`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`, which needs no root. A Linux host
skips the Apple targets in `build`, which is where CI and the release run.

### Shared code

Everything reusable lives in `libs/core/stx-common` (kotlinx only, no other dependency), and a helper
moves there when a *second* caller appears, not before. A data-access library offers extensions, not a
base class. Anything `AutoCloseable` closes idempotently through `CloseGuard`. A library edge in an
integration module is `exported`, not `compile-only`. The reasoning, the concurrency types
(`Mutex`, `Mailbox`, ...) and the integration-module layout are in [`docs/shared-code.md`](docs/shared-code.md).

### Local services

Nothing needs a service running and nothing finds one by guessing: Docker containers start per run, or
the exported variables point at a server you already have. **Run `docker ps` before pulling an image or
starting a container, and stop what you start**: the services do not all fit on a 16 GiB laptop. A spec
never flushes or touches what it did not create. Resolution order, CI, the Kafka cluster, credentials and
`/etc/hosts` are in [`docs/local-services.md`](docs/local-services.md).

## Module layout

Sources in `src/`, tests in `test/`, test-only resources in `testResources/`:

```yaml
# libs/<role>/<name>/module.yaml
product: jvm/lib          # or kmp/lib, jvm/app, ...

apply:
  - //common.module-template.yaml   # shared config, see below

dependencies:
  - //libs/core/stx-common          # another module — path from the project root
  - $libs.kotlinx.serialization     # project catalog alias

test-dependencies:
  - $libs.kotest.runner.junit5
```

**Local modules come first in every dependency list**, before any `$libs.*` or `$spring.*` alias.
A module's own composition — what it is built out of — reads before what it borrows from the
outside, and a `//libs/` entry buried between two catalog aliases is the one a reader misses when
asking what a module actually depends on.

Modules are registered in `project.yaml`, and this repo registers them **by glob**, so a new module under `libs/<role>/`, `libs/<role>/<family>/`, `plugins/`, `examples/<name>/` or `examples/<group>/<name>/` is picked up without editing the file:

```yaml
modules:
  - examples/*
  - examples/*/*
  - libs/*/*
  - libs/*/*/*
  - plugins/*
```

Only directories that directly contain a `module.yaml` are matched, so grouping directories — `examples/material-demo`, the role folders under `libs/`, a family such as `libs/api/stx-graphix` — and every `src/`, `test/` and `build/` are ignored. Two ways a glob goes wrong: `**` is rejected — express depth with successive `*` segments, which is why `examples/*` and `examples/*/*` are both listed — and a pattern matching *nothing* is reported (a weak warning, re-measured on 0.13.0, seen when `libs/*` outlived the last module directly under `libs/`), so don't add a line for a directory that doesn't exist yet. There is no nesting: one `project.yaml` defines the project root, it has no include directive, and module dependencies may not cross a project boundary.

**`libs/` is grouped by role, and a role folder is ownership, not a build boundary.** Six folders,
each with its own line in `.github/CODEOWNERS`:

| folder | libraries |
| --- | --- |
| `libs/core` | stx-common, stx-testing, stx-ktor, stx-spring-boot |
| `libs/data` | stx-jpa, stx-mongo, stx-redis, stx-storage, stx-migrations |
| `libs/messaging` | stx-amqp, stx-kafka, stx-workflow |
| `libs/api` | stx-graphix, stx-openapi-generator, stx-i18n |
| `libs/observability` | stx-telemetry |
| `libs/ui` | stx-material |

Nothing in the build knows about them. A module in `libs/data` depends on one in `libs/core` by `//`
path exactly as before, and families cross roles freely — `stx-workflow-db` needs jpa, mongo and
redis, `stx-spring-boot` needs i18n and mongo-spring. That is also why this is one repository:
splitting by role was weighed and rejected, because every such edge would become a published-version
hop and a change to `stx-common` a release in every other repository. A new library goes in the
folder of the role it serves; if none fits, add a folder, a `CODEOWNERS` line and a row here.

**A library with framework integrations is a family directory.** `libs/<role>/<name>/` then contains
no `module.yaml` of its own; the library itself is `libs/<role>/<name>/<name>/`, and each framework
integration is a sibling beside it — `libs/data/stx-jpa/stx-jpa`, `…/stx-jpa-ktor`,
`…/stx-jpa-spring`. That is what lets an integration be published, versioned and depended on without
the hub it used to live in. A library with none — `stx-common`, `stx-testing`, `stx-ktor`,
`stx-spring-boot`, `stx-openapi-generator`, `stx-material` — sits directly at `libs/<role>/<name>/`.
Either way a module's name is its own directory name, so neither the family nor the role folder
changes a coordinate: `libs/data/stx-jpa/stx-jpa` publishes as `io.github.softistx:stx-jpa`.

Rules that are easy to get wrong:

- **A module's name is its directory name, and it must be unique across the whole project.** There is no `name:` property in `module.yaml` (it fails with `Unknown property`), and a `modules:` entry is a path string, not a mapping — so two directories called `android` under different parents abort *every* command with `Module name 'android' is not unique`. `-m` takes the bare name only, never a path, so there is no way to disambiguate after the fact. Hence `md-catalog`/`md-desktop`/`md-android` rather than `catalog`/`desktop`/`android`: prefix a demo's modules so the next demo can have the same shapes. `description:` gives a module a readable label in `kotlin show modules`, but does not change its name.
- **Module dependency paths start with `//`** and are relative to the project root. A bare `libs/core/stx-common` is read as an *external* Maven coordinate; relative forms (`./nested`, `../sibling`) work but the docs warn they may be deprecated.
- `module.yaml` is schema-validated: an unknown property fails the command with a pointer to the offending line, so a passing `kotlin show modules` is a cheap syntax check after editing a manifest.
- Dependencies are **not transitive at compile time** — a dependent module sees a library only if the producing module marks it `exported: true` (Gradle's `api()`).
- Tests use kotlin-test with **JUnit 5** by default on JVM/Android; change via `settings.junit`.

## Dependency reuse

`libs.versions.toml` at the repo root is the project catalog and **the single place where dependency versions live**. Any dependency used by more than one module — and by default any dependency at all — goes in the catalog and is referenced from modules as `$libs.<alias>`; do not paste versioned Maven coordinates into a `module.yaml`. Alias keys map with dashes becoming dots: `kotest-runner-junit5` → `$libs.kotest.runner.junit5`. Toolchain-provided catalogs (`$kotlin.*`, `$compose.*`) need no catalog entry.

**The toolchain does not read `[bundles]`** — `$libs.bundles.<name>` fails with `No catalog value for the key`. The bundles in this catalog only document which stack a dependency belongs to (nothing in the repo consumes them), and modules must list individual aliases. The catalog serves this repo's own modules and nothing else; an alias no module, template, script or doc example names is deleted rather than kept for a hypothetical consumer.

**The 17 `stx-*` keys in `[versions]` are one per library family, and a script owns them.** Every
`stx-*` alias in `[libraries]` takes `version.ref = "<its family>"` — `stx-jpa-ktor` and
`stx-jpa-spring` both point at `stx-jpa` — so that an example resolves the same version the family
publishes under. `scripts/sync-version.ts` writes every one from the families' `package.json`; never
edit one by hand, and when you add an alias for an artifact that had none, give it its **family's**
ref rather than a version of its own. A family's name is also a `[libraries]` alias, which is why
the script scopes its rewrite to the `[versions]` table.

To share a dependency *set* or settings across modules, use a **module template**: a `<name>.module-template.yaml` with the same shape as `module.yaml` (minus `product:`), pulled in with `apply: [ //name.module-template.yaml ]`. Templates merge dependencies, settings, and repositories, and can apply other templates; check the result with `kotlin show settings -m <module>`.

The catalog's `[bundles]` groupings map to the intended consumer surfaces of this library, which is the fastest way to see which stack a new module belongs to:

- **`compose`** / `compose-test` / `compose-android-test` / `compose-ksp` — Android + Compose client: Material3, Navigation Compose, Room, DataStore, WorkManager, Koin, kotlinx-rpc client.
- **`ktor`** / `ktor-mix` / `ktor-test` — server: Ktor, kotlinx-rpc server, MongoDB Kotlin coroutine driver, Koin, Konform validation, Nimbus JOSE JWT, Spring Security crypto, simple-java-mail, Kotest.
- **`shared`** / `shared-test` — code common to both: kotlinx-serialization, kotlinx-rpc client, bson-kotlinx.
- **`kotlinx`** — coroutines/datetime.

**A module that turns on `settings.ktor` pins the version to the catalog's.** `ktor: enabled` gives
`$ktor.server.core` and the rest from the toolchain's *own* default version, which matched
`ktor = "3.5.2"` in the catalog on 0.12.x by coincidence rather than by construction. Toolchain
0.13.0 moved the default to 3.6.0 while the catalog was still on 3.5.2 — exactly the upgrade this
rule was written for (the catalog has since caught up): without the pin, an artifact this repo names itself (`ktor-server-di`, on
`version.ref = "ktor"`) would now be a different Ktor from `ktor-server-core`. So:

```yaml
settings:
  ktor:
    enabled: true
    version: 3.6.0   # matches `ktor` in libs.versions.toml
```

`./kotlin show settings -m <module>` says which one is in force: `# module.yaml` when it is pinned,
`# default` when the toolchain is choosing. Eighteen modules enable it — `stx-ktor`, every `*-ktor`
integration beside its library, the five Ktor examples and `server/oauth` — and every one of them carries the pin.

**`kotlinx-serialization` follows the toolchain the same way.** `settings.kotlin.serialization: json` makes the toolchain add `kotlinx-serialization-core` at its own default (1.11.0 on 0.13.0), so the catalog's `kotlinx-serialization` (the `-json` artifact) stays at that version: a higher one would put `json-jvm:1.12.0` next to `core-jvm:1.11.0` in the POMs, and Maven's nearest-wins rule would keep core 1.11 under json 1.12. Check `./kotlin show settings -m stx-common` and `./kotlin show dependencies -m stx-common` on every toolchain bump.

The catalog's `kotlin = "2.4.20"` entry is for consumers that need an explicit Kotlin version; the toolchain supplies its own compiler and stdlib (2.4.20 with CLI 0.13.0), so that entry does not control what this repo compiles with — nor what it publishes: every POM declares `kotlin-stdlib` at the toolchain's version, so a toolchain bump that moves the compiler moves that line in every family's next release. Spring Boot is the same — `springBoot: enabled` without a `version` imports the toolchain's default `spring-boot-dependencies` BOM into the POM (4.1.1 on 0.13.0, 4.1.0 on 0.12.2).

A compiler plugin is tied to the compiler, and a bump can break it where no source changed. 0.13.0's Kotlin 2.4.20 failed `server/oauth` inside the Koin compiler plugin 1.1.0 — an `IrGenerationExtensionException` on a removed `getValueArgument` overload, preceded by Koin's own warning that 2.4.20 is newer than it was tested on. `koin-compiler = "1.2.1"` compiles it. Read that warning as the failure's first line, not as noise.

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
- **The one deliberate block on a client's delivery thread: `AmqpConsumer`'s `trySendBlocking`.**
  `DeliveryThreadsTest` asked the RabbitMQ client: deliveries arrive on the connection's shared
  pool, work is serialised per channel, and a parked callback delays only its own channel — until
  as many are parked as the pool has threads, when every other consumer on the connection starves.
  So `ConsumerOptions` refuses a prefetch above `MAX_PREFETCH` (64; the flow's buffer is twice that)
  and `0`, with a message giving the limit and no silent clamp. Within it the buffer never fills —
  the 2x headroom is for connection recovery, when redelivered messages sit beside stale buffered
  ones — so the block is bounded backpressure that returns at once. An auto-ack consumer
  (`OnDelivery`, no prefetch) uses a non-blocking `trySend` and fails the flow with
  `AmqpBufferFullException` when full, so it never blocks. Everywhere else, never block a client's thread.
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
  including the one trap left: `limit` and `offset` silently truncate whenever a query loads a
  collection, whether by `fetchEach` or by a graph naming one. Only `findPage` enforces it — it throws
  `IllegalArgumentException` naming the collection it was asked to fetch; `select(...).limit(...)` and
  a graph on a query still say nothing, so the caller pages owners and collections separately.
- **Keep the fast tests fast and the slow ones optional.** Timing claims — a full buffer pausing, a
  strategy committing when it says it does, partitions running concurrently — belong on Kafka's own
  `MockConsumer`/`MockProducer` and run in about two seconds. A real server is for behaviour that
  *is* the server's: an acknowledgement removing a message, a TTL expiring one. Those specs skip
  when the server is unreachable, so a machine without it reports skipped rather than red.
- **Watch what the machine is carrying.** These containers do not all fit at once on a 16 GiB
  laptop: the three-broker Kafka cluster alone is around 1.9 GiB, and starting it alongside
  everything else once drove one into the OOM killer, which chose the running IDE rather than
  anything of ours. Check `docker ps` first, stop what you started, and prefer the specs that need
  nothing running.

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
  | `CONTRIBUTING.md` | How does someone who is not a maintainer set up, open a pull request, and pass review? The short form of this file. |
  | `docs/consuming.md` | How do I depend on `io.github.softistx:stx-*` from Gradle, Maven or the toolchain? **This is where a new repository, credential mechanism or packaging caveat is documented.** |
  | `docs/releasing.md` | How is a version cut and published? The per-family circuit, the changeset rules, and the publishing behaviours that are not guessable. |
  | `docs/openapi-support.md` | What does the generator understand of an OpenAPI document? **This is where support for a new keyword, format or extension is documented** — it is the part that grows every phase. |
  | `libs/api/stx-openapi-generator/README.md` | How is the module shaped, what does each emitter produce, how do I add one? Roughly constant in size. |
  | `plugins/openapi/README.md` | How do I turn this on in a module, and what does that need on its classpath? |
  | `plugins/dgs-codegen/README.md` | How do I generate DGS types from SDL in a toolchain module |
  | `plugins/apollo/README.md` | How do I generate Apollo models and `OPERATION_DOCUMENT` from schema + documents |
  | `libs/core/stx-common/README.md` | What belongs in the shared module, which concurrency type a given caller wants, the `getOrPut` trap, and the table of what the standard library already covers so nothing here wraps it twice |
  | `libs/messaging/stx-amqp/stx-amqp/README.md` | The same, for AMQP — topology, confirms, prefetch, and why a retry is a queue nobody consumes |
  | `libs/messaging/stx-amqp/stx-amqp-ktor/README.md` | The Ktor plugin for it — the connection/channel split that decides its shape, and the blocking connect |
  | `libs/messaging/stx-amqp/stx-amqp-spring/README.md` | The Spring auto-configuration for it — the same connection/channel split, from the container's side |
  | `libs/api/stx-i18n/stx-i18n/README.md` | The same, for i18n — the locale walk, what eager compilation buys, and why `ResourceBundle` is not underneath it |
  | `libs/api/stx-i18n/stx-i18n-spring/README.md` | The Spring auto-configuration for it — why narrowing the locale resolver is the half that matters, and why Boot's own property wins |
  | `libs/api/stx-i18n/stx-i18n-ktor/README.md` | The Ktor plugin for it — the one that owns nothing and resolves per request, and why `?lang=` is a decision rather than a default |
  | `libs/core/stx-ktor/README.md` | The Ktor seam — the four lifecycle verbs and the one rule behind them, the two facts about Ktor's container that unconditional injection rests on, and why no integration lives here |
  | `libs/data/stx-jpa/stx-jpa/README.md` | The same, for Postgres — the confinement rule the library is built around, and why entities need two compiler plugins. Roughly constant in size |
  | `libs/data/stx-jpa/stx-jpa-ktor/README.md` | The Ktor plugin for it — the factory/session split, the blocking bootstrap, and why the `stx-jpa` edge is `exported` here where the hub had it `compile-only` |
  | `libs/data/stx-jpa/stx-jpa-spring/README.md` | The Spring auto-configuration for it — the two beans, the required `packages`, and why `SchemaMode` defers to stx-migrations |
  | `docs/jpa-criteria.md` | What a stx-jpa query may say — the operators, joins, fetch joins, entity graphs, projections, function vocabulary and the two escapes. **This is where a new operator or function is documented** |
  | `docs/jpa-mapping.md` | What a stx-jpa entity may say — the database, column naming, identifiers, `Instant`/`Uuid`, JSON columns, validation. **This is where a new `SqlTypes` code, strategy or converter is documented** |
  | `docs/examples-in-docker.md` | How do I run an example in a container, on the workspace's databases, behind Traefik? The dev container, the `-prod` profile per example, the `db-init` services, and what has to be up first. **This is where a new example's compose service is documented** |
  | `docs/graphix.md` | What a stx-graphix schema may say — roots, types, scalars, field directives, validation. **This is where a new annotation, scalar or directive is documented** |
  | `docs/graphix-resolvers.md` | What a resolver may be and see — `@Argument`, registered context types, DataLoaders, interceptors, exception handlers, `fromKoin()` |
  | `docs/graphix-serving.md` | Serving a schema — SDL scan, `execute`, HTTP, SSE, graphql-ws |
  | `libs/api/stx-graphix/stx-graphix/README.md` | How the GraphQL engine is shaped, why SerialDescriptor and not Jackson, why there is no class scan in core |
  | `libs/api/stx-graphix/stx-graphix-ktor/README.md` | The Ktor plugin — path, `instance` vs `schema { }`, the call on every operation, and the engine it registers with the container |
  | `libs/api/stx-graphix/stx-graphix-spring/README.md` | The Spring Boot plugin — `stx.graphix.enabled`, `@GraphQLController` scan |
  | `libs/api/stx-graphix/stx-graphix-koin/README.md` | Building the schema from a Koin container — `fromKoin()`, `GraphixResolver` |
  | `docs/telemetry.md` | What a stx-telemetry call may say — the root's settings, every log and span verb, the severities, the attribute conversions, `traceparent` and the signal model. **This is where a new verb, severity, span kind or exporter is documented** |
  | `libs/observability/stx-telemetry/stx-telemetry/README.md` | How the telemetry library is shaped — why a coroutine context element and not an MDC, why the thread-local mirror is not a contradiction, why writing a signal never waits |
  | `libs/observability/stx-telemetry/stx-telemetry-otlp/README.md` | The OTLP exporter — why not the Java SDK, the encoding details a receiver is strict about, what is retried and why a partial success is not |
  | `libs/observability/stx-telemetry/stx-telemetry-slf4j/README.md` | The SLF4J bridge — which direction to pick, why reading a third-party MDC is not a contradiction, and why both at once is refused |
  | `libs/observability/stx-telemetry/stx-telemetry-mongo/README.md` | The MongoDB exporter — the document's shape, the dates, and retention as a TTL index |
  | `libs/observability/stx-telemetry/stx-telemetry-ktor/README.md` | The Ktor plugin — why the span wraps the pipeline, why the name is fixed after routing, and why a thrown handler leaves no status code |
  | `libs/observability/stx-telemetry/stx-telemetry-spring/README.md` | The Spring auto-configuration — why `CoWebFilter` and not `WebFilter`, and the 200 WebFlux never sets |
  | `docs/workflow.md` | What a stx-workflow declaration may say — every verb and every annotation, the step scope. **This is where a new verb, annotation or retry policy is documented** |
  | `docs/workflow-runtime.md` | Running a stx-workflow — starting, the statuses and their transitions, the persisted record, the store contract, the Ktor and Spring wiring, the worker. **This is where a new status is documented** |
  | `libs/messaging/stx-workflow/stx-workflow/README.md` | How the workflow engine is shaped — why checkpointing and not replay, what at-least-once asks of a step, why a fan-out needs an explicit merge |
  | `libs/messaging/stx-workflow/stx-workflow-db/README.md` | Where instances live — why one module and not three, and per store: the index, the conditional write, the lease and retention |
  | `docs/migrations.md` | What a stx-migrations migration may say — the version, the statuses and their transitions, the runner's order, the ledger contract, the lock, and what a killed process leaves per store. **This is where a new status, ledger method or store is documented** |
  | `libs/data/stx-migrations/stx-migrations/README.md` | How the migration library is shaped — why the version is declared and not parsed, why a gate throws where the prior art logged, and why neither Flyway nor Liquibase is underneath it |
  | `libs/data/stx-migrations/stx-migrations-db/README.md` | Where the ledger lives — why one module and not two, and per store: the uniqueness, the lock, the instants, and how DDL reaches the database |
  | `libs/data/stx-migrations/stx-migrations-ktor/README.md` | The Ktor plugin — why `runBlocking` in `install` is the gate, why it goes after the connection plugin, why it owns and closes nothing, and why the `sql { }` / `mongo { }` DSL ends in `gate` |
  | `libs/data/stx-migrations/stx-migrations-spring/README.md` | The Spring auto-configuration — `stx.migrations.*`, why an `InitializingBean` and not a suspending listener, and why nothing is inferred about where the ledger goes |
  | `libs/messaging/stx-kafka/stx-kafka/README.md` | The same, for Kafka — the publisher, the poll loop, and why the loop is shaped the way it is |
  | `libs/messaging/stx-kafka/stx-kafka-ktor/README.md` | The Ktor plugin for it — why it owns nothing, and why that is the correct shape rather than a gap |
  | `libs/messaging/stx-kafka/stx-kafka-spring/README.md` | The Spring auto-configuration for it — the one whose context holds no open resource, and why |
  | `libs/data/stx-mongo/stx-mongo/README.md` | How is the Mongo library shaped, and why is each non-obvious part the way it is? |
  | `libs/data/stx-mongo/stx-mongo-ktor/README.md` | The Ktor plugin for it — why construction stays in the library and only the lifecycle is here, and why two handles rather than one |
  | `libs/data/stx-mongo/stx-mongo-spring/README.md` | The Spring auto-configuration for it — and the cross-module ordering that decides which `MongoDatabase` an application gets |
  | `libs/core/stx-spring-boot/README.md` | The Spring integrations — the opt-in `stx.*` model, why the configuration metadata is hand-written, why the locale comes off the exchange, and what `compile-only` buys a consumer |
  | `docs/spring-mongo-queries.md` | What a stx-spring-boot Mongo query may say — the predicate operators, the filter and sort grammars, and the keyset paging rules. **This is where a new operator or filter token is documented** |
  | `docs/spring-configuration.md` | Every `stx.*` key, its default and what enabling it costs. **This is where a new configuration key is documented** |
  | `docs/spring-web.md` | What the stx-spring-boot web layer may say — routes, typed clients for other services, and the annotations that say who is calling. **This is where a new route extension, client factory or security annotation is documented** |
  | `docs/shared-code.md` | What belongs in `stx-common` and what does not, the concurrency types, extensions rather than a base class, how a framework integration is a sibling module, `exported` against `compile-only`, and `CloseGuard`. |
  | `docs/local-services.md` | How specs and examples find Postgres, Mongo, Redis, Kafka, AMQP and MinIO: the resolution order, CI, the Docker workspace, credentials and `/etc/hosts`. |
  | `docs/spring-testing.md` | What an application's specs write on stx-spring-boot's test harness, and why `MongoSpec` finds Mongo, the database and the port the way it does |
  | `libs/data/stx-redis/stx-redis/README.md` | The same, for Redis — including what each layer deliberately does not do |
  | `libs/data/stx-redis/stx-redis-ktor/README.md` | The Ktor plugin for it — why the connection is shared, why `config` is defaulted where `MongoDB`'s `uri` is not, and the double close |
  | `libs/data/stx-redis/stx-redis-spring/README.md` | The Spring auto-configuration for it — the opt-in rule, and what deliberately is not a property |
  | `libs/data/stx-storage/stx-storage/README.md` | The same, for object storage — and what a presigned URL can and cannot promise |
  | `libs/data/stx-storage/stx-storage-ktor/README.md` | The Ktor plugin for it — and why a credential with a default is a credential in source control |
  | `libs/data/stx-storage/stx-storage-spring/README.md` | The Spring auto-configuration for it — the same credential rule, asserted from both sides by its wiring spec |
  | `libs/ui/stx-material/README.md` | How is the UI library shaped, how does `StxTheme` slot into an application that already uses Material 3, and how do I add a component? |
  | `libs/ui/stx-material/docs/tokens.md` | What a token may say — the colour roles, spacing, durations and easings, and why shapes and elevation are M3's. **This is where a new token is documented** |
  | `libs/ui/stx-material/docs/components.md` | Every component, its parameters, and its story in the catalogue. **This is where a new component is documented** |
  | `libs/ui/stx-material/docs/troubleshooting.md` | What a consumer hits and the fix, one entry per symptom (today: animations that differ between machines, from the OS "Reduce motion" setting) |
  | `examples/material-demo/md-school/README.md` | What the School app is, how to run and test it, and where its README lives (the demo README holds the rest) |
  | `examples/material-demo/md-school/docs/design-grid.md` | How a visual slice of the School app is scored: the criteria, the 1 to 5 scale, and the bench it is read against |
  | `libs/ui/stx-material/docs/roadmap.md` | Where the library is — the phases and what each delivered. **A box is ticked in the change that delivers it, never after** |
  | `examples/artifacts-ktor/README.md` | Why two modules exist that demonstrate nothing: the nine coordinates of `stx-amqp`, `stx-kafka` and `stx-storage` that no other module resolves, and what the compile and the spec each prove about a POM |
  | `examples/artifacts-spring/README.md` | What booting a context proves that loading a class cannot — and the two things that bit while writing it |
  | `examples/spring-orders/README.md` | What each file in the Spring demo is there to show, how to run it, and what it deliberately leaves out |
  | `examples/graphix-shop/README.md` | What the GraphQL catalogue shows — split SDL, annotated DataFetchers, how to run it |
  | `examples/graphix-codegen/README.md` | The two GraphQL codegen plugins on one schema |
  | `examples/workflow-checkout/README.md` | What the checkout saga shows — the journal each run leaves, and the window between an effect and its checkpoint that the idempotency key exists to cover |
  | `examples/material-demo/README.md` | Why the demo is four modules, how to run it, and how a story is registered |
  | `libs/core/stx-testing/README.md` | Where an integration spec's server comes from, how a container declared there is cleaned up, and the two conventions every harness follows — `requireEndpoint()` and `TestNames` |
  | `AGENTS.md` | How do I work in this repo? One paragraph per capability, never the detail. |

  When a README section starts growing every phase, that is the signal it belongs in `docs/`, not
  the signal to keep appending. `libs/api/stx-openapi-generator/README.md` reached 394 lines before its
  reference half moved out; splitting on *audience* rather than on length is what made the seam
  obvious. `libs/data/stx-jpa/stx-jpa/README.md` reached 921 and split the same way, into the query vocabulary
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
- **Tests are kotest `FeatureSpec`, grouped by scenario.** Behaviour specs are kotest `FeatureSpec` (a Spring wiring spec may be a `StringSpec`),
  with `feature("...")` naming the behaviour under test and `scenario("...")` naming one case of it:

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

  The exception is a Spring wiring spec, one that only checks beans are wired and configured: it has
  no behaviour to group, so it may be a `StringSpec` with flat `"..."` tests, as
  `libs/core/stx-spring-boot/test/data/mongo/config/MongoAutoConfigurationTest.kt` is. Any spec that
  tests behaviour stays a `FeatureSpec`.
- **Every module's packages start with `com.softistx`.** The rest follows the module: `com.softistx.openapi` for `libs/api/stx-openapi-generator`, `com.softistx.openapi.plugin` for `plugins/openapi`, `com.softistx.demo.api` for `examples/demo-api`. Generated code follows the same rule — the `openapi` plugin's `packageName` setting is set per module, and defaults to `generated.api` only when nobody sets it.
- **Organise by package, not as a flat pile of files — `test/` exactly as much as `src/`.** A module
  with more than one concern gets a directory per concern, and the directory matches the package —
  `src/parser/` is `com.softistx.openapi.parser`. The root package holds only what every package
  depends on: the shared contract, nothing else. When a file lands in the root because it did not
  obviously belong anywhere, that is the signal a package is missing.

  A test tree is not exempt, and it is where this slips: a fixture gets written beside whichever spec
  needed it first, and three specs later the fixtures are scattered across four packages with no rule
  anyone could state. **Test fixtures live in a package named for what they are, not for the spec that
  happened to need them first** — entities in `test/entity/`, and the same for any other family of
  fixture a module grows. A spec imports its fixtures; it does not host them. `stx-jpa` and
  `stx-jpa-ktor` both keep their JPA entities in `…entity`.

  One exception, and it has to be argued in the file: a spec that is *about* a package boundary owns
  the package it scans. `stx-jpa`'s `EntityScanTest` needs a package holding nothing but the
  classes it expects to find, which is why those fixtures sit in `test/entity/scan/` instead of
  beside the rest.
- `.gitignore` excludes `build`, `.idea`, and `.jbeval`; build output goes to `build/` under the project root unless `--build-dir` overrides it.
- **A pull request that changes a library carries a changeset, and the changeset names the
  family.** `bun changeset`, pick the families, pick patch/minor/major, write one sentence a
  *reader* of the release notes will see — not a restatement of the commit subject — and commit the
  `.changeset/*.md`. CI fails the PR without one, **naming the families you changed and did not
  declare**, because it is the only step of the release circuit nobody can automate — and because a
  changeset for the wrong family is worse than none: it releases a library nobody touched and leaves
  the touched one behind. `bun changeset --empty` is the explicit "nothing published changes" and
  satisfies the guard. A name that is not one of the families is refused. A `major` whose
  dependents are not named **fails**, naming them and printing the lines to paste: Changesets only
  ever gives a dependent a `patch`, and the POM pins an exact version, so a consumer would take the
  break on a patch. The escape hatch is an `unaffected: <names>` line in the changeset's prose, for
  a break that genuinely cannot reach that dependent's own consumers; a name on it that depends on
  no `major` in the changeset is refused as the typo it is. `.changeset/README.md` has the rest.
- **`develop` is where work lands and every PR targets it.** Branch off `develop`, open the
  pull request against `develop`, and merge it there. Nothing is merged directly into `main`, however
  small and however green — a PR opened against `main` has the wrong base and wants recreating, not
  merging.

  One exception, and only one: the slices of a feature too large for a single PR target that
  feature's own integration branch, and that branch is what targets `develop`. See
  `large-feature-branch-workflow`, which owns the shape. Everything else is one branch, one PR,
  base `develop`.
- **`main` is fast-forwarded to `develop` right after every merge into `develop`.** Aligning is not
  part of writing a feature, but it follows each merge at once, as in the other repositories. It is
  `git checkout main && git merge --ff-only develop` (or `git push origin develop:main`) — fast-forward
  only: never merging a feature branch into `main`, never cherry-picking across, never forcing.

  If `develop` is *behind* `main`, someone has written to `main` directly and that is the thing to fix
  first: merge `main` into `develop`, then align `main` from the result. No history is rewritten
  either way.

  The failure this prevents is quiet: merging features into `main` while `develop` sits behind works
  perfectly until `develop` carries real work of its own, and then the two have genuinely diverged
  with no single branch holding everything. It has already happened once here — three PRs landed on
  `main` while `develop` was nine commits behind, and it was harmless only because `develop`'s three
  extra commits were merges carrying no file changes at all.
