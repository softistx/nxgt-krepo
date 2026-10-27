# stx-material

A Compose Multiplatform component library: a token layer over Material 3, components that need one
line for their common case, and motion that is on by default rather than opt-in.

**`io.github.softistx:stx-material`** — [how to depend on it](../../../docs/consuming.md).

It is the repo's first client-side module — the first `kmp/lib`, the first `settings.compose`, the
first `android` target. `examples/material-demo` is its catalogue and its test bench.

```
libs/ui/stx-material/
  src/foundation/            what everything else is built from            (com.softistx.material.foundation.*)
    theme/    tokens, StxTheme, the colour seed, and the platform scheme's expect
    style/    StxStyles — every component default in one place — and the StyleScope token extensions
    motion/   the M3 MotionScheme, transitions, shimmer, stagger
    layout/   the window-size and responsive helpers (Breakpoints, wCompact, LocalWindowSize, …)
    text/     Typography and the variant scale
    icon/     Icon and the library's own icon set
  src/components/            one folder per family, listed in docs/components.md  (com.softistx.material.components.*)
    button/ form/ (+ state/) navigation/ layout/ feedback/ content/ surface/ media/ data/ datetime/
  src@android/foundation/theme/  the wallpaper palette          ┐ the only platform-specific
  src@jvm/foundation/theme/      the seed                       │ decision in the library
  src@ios/foundation/theme/      the seed                       ┘
```

A folder is a package: `src/components/button/Button.kt` is `com.softistx.material.components.button`.
A component's `*Styles.kt` sits beside it. `foundation` holds no components: the layout components live in
`components/layout/`, and `foundation/layout/` keeps only the window-size helpers.

## The shape of it

**`StxTheme` takes Material 3's own inputs.** A `ColorScheme`, a `Typography`, `Shapes` and a
`MotionScheme`, each with a default — the same four `MaterialTheme` takes. It wraps M3 rather than
replacing it, so a plain M3 component, or any third-party M3 library, keeps working inside it. That
is what makes this adoptable in an application that already exists, and a caller that already
computes one of the four passes it and keeps the other three.

It installs `MaterialExpressiveTheme` and `MotionScheme.expressive()`: rounder shapes, springier
motion. `motionScheme = MotionScheme.standard()` calms it for the whole tree, and every
animation follows — nothing here holds its own curve.

**It follows the OS "Reduce motion" setting** on Android and iOS when you pass neither `motionScheme` nor
`motion`; pass either one and the OS is not read. Desktop reads only `-Dstx.material.reduceMotion`, and an app
can opt in to the OS's answer with `readDesktopReducedMotion()`:

```kotlin
StxTheme(motion = StxMotion(enabled = false)) { App() }   // opt out: no animation, whatever the OS says
```

```kotlin
// An application: one line, and the platform decides where the scheme comes from.
StxThemeProvider(seed = Color(0xFF5B5BD6)) {
    Button("Save changes", onClick = ::save)
}

// Anything more specific goes straight to StxTheme.
StxTheme(colorScheme = brandScheme, motionScheme = MotionScheme.standard()) { … }
```

**Only one decision is platform-specific**, and it is behind `expect`/`actual`:
`platformColorScheme(seed, isDark, dynamicColor, paletteStyle, specVersion)` reads the user's wallpaper palette on Android 12+
and falls back to the seed everywhere else. `supportsDynamicColor` says which, so a settings screen
can decide whether to offer the choice at all. Everything above that — the provider, the tokens,
every component — is written once in the common source set. `paletteStyle` and `specVersion` act on
the seed path only; the default is a calm TonalSpot palette on `SPEC_2025`, described under
"A calm palette" in [docs/tokens.md](docs/tokens.md#colour).

The rest of the palette derives from one seed by [material-kolor], including the `success` and
`warning` roles Material 3 does not define, each from its own seed; `info` follows the scheme's
`secondary` unless a colour is passed. Those roles are added to *whatever* scheme arrives, seed or
wallpaper. `error` is not re-derived — it is delegated to the M3 scheme, so there is exactly one
red.

**A component here is usually Material 3's, dressed.** `Button` is M3's `Button`, `Chip` its
`FilterChip`, `Card`, `ListTile` and `StatusBadge` its `Card`, `ListItem` and `Badge`. Nothing that
M3 already ships is rebuilt from primitives — M3 gets the ripple, the disabled treatment, the
selected semantics and the accessibility right, and rebuilding a component throws all of that away
to reproduce a container. What this library adds is the default that was missing: the colour matrix
resolved once, the padding and rhythm inside a card, the hover state M3's chip does not have.
`Alert`, `EmptyState`, `Skeleton`, `ResponsiveButton`, `Rating`, `Stat`, `LabeledDivider`,
`FilterBar`, `UploadField`, `StatusDot`, `Kbd`, `QuantityField`, `AvatarGroup`, `InlineEdit`,
`ExpandableText`, `CodeBlock`, `SectionHeader`, `RelativeTime`, `ConfirmButton`,
`PasswordMeter`, `Disclosure` and `SelectionBar` are built from primitives because M3 has nothing
to start from. AGENTS.md's *Building a component* is the rule.

**Colour, shape, border and padding go through M3's own `*Colors` and `*Defaults`.** A `Style` is
for what M3 has no parameter for:

```kotlin
// The 5 × 7 matrix, resolved once, in the shape M3 accepts.
fun buttonColors(variant: ButtonVariant, color: ButtonColor): ButtonColors = …

// The whole button family (Button, IconButton, IconToggle, ToggleButton, SplitButton, Fab)
// morphs its shape under the finger, so its one Style keeps only the disabled alpha.
// StxStyles.button names it.
val buttonStyle: Style = Style {
    disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
}
```

A `background()` in a style block paints behind a surface M3 has already painted — it either does
nothing or hides the state M3 was showing. That is the sign the value belonged in a `*Colors`.

Because interaction states are declared rather than remembered, a caller never holds a `pressed` or
`hovered` flag, and never writes an `animate*AsState` to move between two looks.

**Every interactive component takes `style: Style = Style`** — the identity style, never a named
default. The component applies its own base first, so `style` is an override layered on top:

```kotlin
Card(style = StxTheme.styles.card then { alpha(0.6f) })
```

## Adding a component

The order of work, the checks and the proof are the `stx-material-components` skill
(`.agents/skills/stx-material-components/SKILL.md`); AGENTS.md's *Building a component* and
*Styling a component* hold the rules. In short: M3 first, a `Style` for the rest, its story, its
`docs/components.md` row and `docs/roadmap.md` box in the same change, and a pixel spec in
`test@jvm/` for any claim about pixels.

## Platforms

`[ jvm, android, iosArm64, iosSimulatorArm64 ]`. `iosX64` is not a valid target — the Compose
artifacts do not publish for it.

**A green `./kotlin build` on this Linux host says nothing about the Apple targets**: they are
resolved into the dependency graph and never compiled — the build prints `[jvm]` and `[android]` and
exits 0.

**`./kotlin publish` is a different matter, and this file used to get it wrong.** It cross-compiles
both targets on Linux and writes real klibs — measured at 0.2.1, `stx-material-iosarm64-0.2.1.klib`
is 840 KB with 213 entries and real IR bodies. So no Apple host is needed to produce the published
artifacts, and CI covers the Apple targets through its `publish mavenLocal` step rather than through
the build. What is still uncovered is linking, and there are no iOS tests. The
`compose-multiplatform` skill records the rest of what the toolchain actually does here.

## Publishing

`io.github.softistx:stx-material:0.2.3`, like every other module under `libs/` — but a `kmp/lib` publishes one
artifact per platform beside the root one: `stx-material-jvm`, `stx-material-android`,
`stx-material-iosarm64`, `stx-material-iossimulatorarm64`. A consumer depends on the root artifact
and the platform one is selected for it.

**`composeResources` are not part of the publication yet** ([KTC-5698][ktc-5698]). The jar publishes
and the components work; anything this library ever ships as a Compose resource would not reach a
consumer. Nothing here does today — worth knowing before the first one is added.

[ktc-5698]: https://youtrack.jetbrains.com/issue/KTC-5698/Support-publication-of-composeResources-as-a-part-of-KMP-library-publication

## Where to read next

| | |
| --- | --- |
| [`docs/roadmap.md`](docs/roadmap.md) | Where the library is — the phases, ticked as they land |
| [`docs/tokens.md`](docs/tokens.md) | What a token may say: colours, spacing, durations, easings — shapes and elevation are M3's |
| [`docs/components.md`](docs/components.md) | Every component, its parameters, and its story in the catalogue |
| [`docs/troubleshooting.md`](docs/troubleshooting.md) | Errors and surprises, headed by what you will see |
| [`../../examples/material-demo/`](../../../examples/material-demo) | The catalogue: `./kotlin run -m md-desktop` |

[material-kolor]: https://github.com/jordond/MaterialKolor

---

Apache-2.0 · [Contributing](../../../CONTRIBUTING.md) · [All the libraries](../../../README.md)
