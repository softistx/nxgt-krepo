---
name: compose-multiplatform
description: Compose Multiplatform for this repo's UI library — targets and what each host can build, resources, lifecycle and ViewModel, navigation, testing, and the $compose catalog the Kotlin Toolchain exposes. Use when writing or debugging shared Compose UI.
---

# Compose Multiplatform

`libs/shared-material` is the repo's Compose Multiplatform library and
`examples/material-demo` its catalogue. Both are built by the **Kotlin Toolchain**, not Gradle,
so every `kotlin { sourceSets { … } }` snippet in `references/` describes the Gradle spelling of
something a `module.yaml` says differently. Translate it; do not copy it.

`references/` caches the official documentation. Read the page rather than answering from memory —
Compose Multiplatform moves faster than memory does.

## What this repo verified against the CLI

Measured on toolchain 0.12.0, Compose 1.11.1, on Linux x86_64. Re-check after a toolchain bump.

- **`iosX64` is not a valid platform for a Compose library.** The Compose artifacts publish for
  `[android, iosArm64, iosSimulatorArm64, js, jvm, macosArm64, wasmJs]` and nothing else, so
  declaring `iosX64` fails dependency resolution with a pointer at the offending dependency line.
- **Apple targets are silently skipped on a non-Apple host.** With `iosArm64` and
  `iosSimulatorArm64` declared, `./kotlin build` on Linux prints only `[jvm]` and `[android]`
  compilations and exits 0. The iOS variants *are* resolved into the dependency graph, so the
  declaration is meaningful — but **a green local build says nothing about the Apple targets.**
  Only a macOS host or CI can break on them.
- **`$compose` catalog keys that exist:** `runtime`, `foundation`, `ui`, `material`, `material3`,
  `animation`, `animationGraphics`, `components.resources`, `uiTooling`, `uiTest`,
  `desktop.currentOs` (jvm only), `hotReload.runtimeApi` (jvm only).
  **Keys that do not exist**, despite being obvious guesses — name a real coordinate in
  `libs.versions.toml` instead: `materialIconsExtended`, `materialIconsCore`,
  `components.uiToolingPreview`, `material3.adaptive`, `material3AdaptiveNavigationSuite`.
- **A missing key reports itself as "Compose is disabled".** The error says to set
  `compose.enabled`, which is misleading when Compose is already on: it means the key is not in
  the catalog. Check the list above before chasing the setting.
- **`$compose.material3` resolves to its own version line** — `1.11.0-alpha07` while `foundation`
  and `ui` are `1.11.1`. Material 3 is an alpha here; that is what `settings.compose.version`
  gives, not a mistake to correct. See the `material3-compose` skill for its real API surface.
- **Kotest runs from the common `test/` tree, but only with the JUnit 5 runner declared per
  platform.** `kotest-framework-engine` alone discovers zero tests and the run fails with exit
  code 2. This works:

  ```yaml
  test-dependencies:
    - $libs.kotest.framework.engine
    - $libs.kotest.assertions.core

  test-dependencies@jvm:
    - $libs.kotest.runner.junit5

  test-dependencies@android:
    - $libs.kotest.runner.junit5
  ```

  Both qualifiers are needed: the Android unit-test run is a second JVM run with its own
  classpath, and omitting `@android` leaves it discovering nothing while `@jvm` passes.
- **The toolchain provisions its own Android SDK.** It downloads `cmdline-tools` and the
  `compileSdk` platform into `~/.cache/JetBrains/Kotlin/`; `ANDROID_HOME` is not consulted and
  need not be set. The default `compileSdk` is 37 and it is fetched on first build.

## The module shape

```yaml
product:
  type: kmp/lib
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64 ]

dependencies:
  - $compose.material3: exported   # exported: its types are in this module's public API
  - $compose.foundation: exported
  - $compose.ui: exported

settings:
  compose:
    enabled: true
    version: 1.11.1     # pin it, like `ktor` is pinned, so a toolchain bump is a visible change
  android:
    namespace: com.strange.material
```

An application module cannot be both: `android/app` supports only the `android` platform and
`jvm/app` only `jvm`, so a demo that runs on both is one `kmp/lib` holding the UI plus two thin
launcher modules.

## Where to read

`references/INDEX.md` is the routing table. The pages worth knowing exist:

| Question | Page |
| --- | --- |
| What is in each Compose version | `whats-new-compose-111.html.md`, `whats-new-compose-112.html.md` |
| Which Compose version pairs with which Kotlin | `compose-compatibility-and-versioning.html.md` |
| Images, strings and fonts in a shared module | `compose-multiplatform-resources*.html.md` |
| Where the composition's lifecycle comes from | `compose-lifecycle.html.md`, `compose-viewmodel.html.md` |
| Navigation, routes, deep links | `compose-navigation*.html.md`, `compose-navigation-3.html.md` |
| Window size classes and multi-pane layouts | `compose-adaptive-layouts.html.md` |
| Testing composables | `compose-test.html.md`, `compose-desktop-ui-testing.html.md` |
| Desktop-only surfaces (tray, menu bar, scrollbars, tooltips, mouse) | `compose-desktop-*.html.md` |
| What differs per platform | `compose-platform-specifics.html.md`, `compose-android-only-components.html.md` |
| Which Jetpack libraries are multiplatform | `compose-multiplatform-jetpack-libraries.html.md` |
| `expect`/`actual` and the source-set hierarchy | `multiplatform-expect-actual.html.md`, `multiplatform-hierarchy.html.md` |
| Hot reload | `compose-hot-reload.html.md` |

Refresh the cache with
`python3 .agents/skills/skill-from-docs/scripts/fetch_docs.py --skill compose-multiplatform`.
