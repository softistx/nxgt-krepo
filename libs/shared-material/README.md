# shared-material

A Compose Multiplatform component library: a token layer over Material 3, components that need one
line for their common case, and motion that is on by default rather than opt-in.

It is the repo's first client-side module — the first `kmp/lib`, the first `settings.compose`, the
first `android` target. `examples/material-demo` is its catalogue and its test bench.

```
libs/shared-material/
  src/theme/    tokens and StrangeTheme
  src/motion/   durations, easings, transitions, shimmer, stagger
  src/style/    StrangeStyles — every component default in one place
  src/text/     Typography and the variant scale
  src/icon/     Icon and the library's own icon set
  src/button/   Button, IconButton, ResponsiveButton, ButtonGroup
  src/display/  Card, Chip, StatusBadge, ListTile, Alert, EmptyState, Skeleton
```

## The shape of it

**`StrangeTheme` wraps `MaterialTheme`, it does not replace it.** It installs the M3 `ColorScheme`
and `Shapes` *and* provides the extra tokens on their own composition locals. The consequence is the
point: a plain M3 `Button`, or any third-party M3 component, keeps working inside it. That is what
makes the library adoptable in an application that already exists.

```kotlin
StrangeTheme(seed = Color(0xFF5B5BD6), isDark = isSystemInDarkTheme()) {
    Button("Save changes", onClick = ::save)
}
```

The whole palette is derived from one seed by [material-kolor], including the `success` / `info` /
`warning` roles Material 3 does not define. `error` is *not* re-derived — it is delegated to the M3
scheme, so there is exactly one red.

**A component's look is a `Style`, in its own file.** This is the repo's default pattern, not an
option; AGENTS.md's *Styling a component* has the rules and `docs/tokens.md` the vocabulary. The
short version:

```kotlin
val chipStyle: Style = Style {
    background(scheme.surfaceContainerHigh)          // reads the theme through StyleScope
    shape(RoundedCornerShape(radii.full))
    selected { animate { background(scheme.secondaryContainer) } }
    pressed  { animate(motion.spec(motion.instant)) { scale(0.97f) } }
}
```

Because interaction states are declared rather than remembered, a caller never holds a `pressed` or
`hovered` flag, and never writes an `animate*AsState` to move between two looks.

**Every component takes `style: Style = Style`** — the identity style, never a named default. The
component applies its own base first, so `style` is an override layered on top:

```kotlin
Card(style = StrangeTheme.styles.card(CardVariant.Elevated) then { border(2.dp, MaterialTheme.colorScheme.primary) })
```

## Adding a component

1. **The style first**, in `src/<area>/<Name>Styles.kt`. Read tokens through `StyleScope`
   (`scheme`, `spacing`, `radii`, `motion`), never through constants. Put the interaction states in
   `pressed` / `hovered` / `disabled` blocks with `animate { }` *inside* them.
2. **The composable**, in `src/<area>/<Name>.kt`. Its signature carries `modifier`, then the
   semantic parameters, then `style: Style = Style` — no `Color`, `Shape` or `Dp` parameters that
   the style already owns. Slots (`leading`, `trailing`, `content`) are `@Composable`, never
   `iconName: String`.
3. **Register its story in the same change**, in `examples/material-demo/catalog/src/stories/`. The
   catalogue is never caught up with afterwards.
4. **Document it in `docs/components.md`** and tick its box in `docs/roadmap.md`, in that same
   change.
5. **Spec whatever does not need a renderer**: token derivation, colour derivation, enum totality,
   path data. Anything that needs a composition has no harness here yet — say so rather than writing
   a test that cannot fail.

## Platforms

`[ jvm, android, iosArm64, iosSimulatorArm64 ]`. `iosX64` is not a valid target — the Compose
artifacts do not publish for it.

**A green build on this Linux host says nothing about the Apple targets**: they are resolved into
the dependency graph but silently skipped at compile time. Only a macOS host or CI can break on
them. The `compose-multiplatform` skill records this and the rest of what the toolchain actually
does here.

## Where to read next

| | |
| --- | --- |
| [`docs/roadmap.md`](docs/roadmap.md) | Where the library is — the phases, ticked as they land |
| [`docs/tokens.md`](docs/tokens.md) | What a token may say: colours, spacing, radii, elevation, durations, easings |
| [`docs/components.md`](docs/components.md) | Every component, its parameters, and its story in the catalogue |
| [`../../examples/material-demo/`](../../examples/material-demo) | The catalogue: `./kotlin run -m desktop` |

[material-kolor]: https://github.com/jordond/MaterialKolor
