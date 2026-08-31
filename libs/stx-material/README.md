# stx-material

A Compose Multiplatform component library: a token layer over Material 3, components that need one
line for their common case, and motion that is on by default rather than opt-in.

It is the repo's first client-side module — the first `kmp/lib`, the first `settings.compose`, the
first `android` target. `examples/material-demo` is its catalogue and its test bench.

```
libs/stx-material/
  src/theme/          tokens, StrangeTheme, and the platform scheme's expect
  src@android/theme/  the wallpaper palette          ┐ the only platform-specific
  src@jvm/theme/      the seed                       │ decision in the library
  src@ios/theme/      the seed                       ┘
  src/motion/   the M3 MotionScheme, transitions, shimmer, stagger
  src/style/    StrangeStyles — every component default in one place
  src/text/     Typography and the variant scale
  src/icon/     Icon and the library's own icon set
  src/button/   Button, IconButton, ResponsiveButton, ButtonRow, Fab, FabMenu, SplitButton, ToggleButton, IconToggle, CopyButton
  src/display/  Card, Chip, ActionChip, StatusBadge, StatusDot, Kbd, ListTile, Alert, EmptyState, Skeleton, Stat, FilterBar, Rating, LabeledDivider
  src/form/     the field/form state layer, the rules, and every input including UploadField, TagField, QuantityField
  src/navigation/ AppBar, NavigationSuite, Tabs, Search, AdaptiveNavDisplay, Stepper, Breadcrumb
  src/layout/     ResponsiveGrid, ScrollToTop, LoadMoreButton, RefreshBox
  src/surface/    ConfirmDialog, Sheet, Drawer, Tooltip, HoverCard, Menu, ContextMenu, Accordion, Carousel, SwipeActions
  src/feedback/   Progress, LoadingMark, Toaster
  src/datetime/   DateField, DateRangeField, TimeField, Calendar
  src/data/       DataTable, CommandPalette, Description, Pagination, EntityHeader, Timeline
  src/media/      Avatar, AvatarGroup, StrangeImage, Gallery, Lightbox, Video/Pdf/Camera surfaces
```

## The shape of it

**`StrangeTheme` takes Material 3's own inputs.** A `ColorScheme`, a `Typography`, `Shapes` and a
`MotionScheme`, each with a default — the same four `MaterialTheme` takes. It wraps M3 rather than
replacing it, so a plain M3 component, or any third-party M3 library, keeps working inside it. That
is what makes this adoptable in an application that already exists, and a caller that already
computes one of the four passes it and keeps the other three.

It installs `MaterialExpressiveTheme` and `MotionScheme.expressive()`: rounder shapes, springier
motion. `motionScheme = MotionScheme.standard()` turns that off for the whole tree, and every
animation follows — nothing here holds its own curve.

```kotlin
// An application: one line, and the platform decides where the scheme comes from.
StrangeThemeProvider(seed = Color(0xFF5B5BD6)) {
    Button("Save changes", onClick = ::save)
}

// Anything more specific goes straight to StrangeTheme.
StrangeTheme(colorScheme = brandScheme, motionScheme = MotionScheme.standard()) { … }
```

**Only one decision is platform-specific**, and it is behind `expect`/`actual`:
`platformColorScheme(seed, isDark, dynamicColor)` reads the user's wallpaper palette on Android 12+
and falls back to the seed everywhere else. `supportsDynamicColor` says which, so a settings screen
can decide whether to offer the choice at all. Everything above that — the provider, the tokens,
every component — is written once in the common source set.

The rest of the palette derives from one seed by [material-kolor], including the `success` / `info`
/ `warning` roles Material 3 does not define; those are added to *whatever* scheme arrives, seed or
wallpaper. `error` is not re-derived — it is delegated to the M3 scheme, so there is exactly one
red.

**A component here is usually Material 3's, dressed.** `Button` is M3's `Button`, `Chip` its
`FilterChip`, `Card`, `ListTile` and `StatusBadge` its `Card`, `ListItem` and `Badge`. Nothing that
M3 already ships is rebuilt from primitives — M3 gets the ripple, the disabled treatment, the
selected semantics and the accessibility right, and rebuilding a component throws all of that away
to reproduce a container. What this library adds is the default that was missing: the colour matrix
resolved once, the padding and rhythm inside a card, the hover state M3's chip does not have.
`Alert`, `EmptyState`, `Skeleton`, `ResponsiveButton`, `Rating`, `Stat`, `LabeledDivider`,
`FilterBar`, `UploadField`, `StatusDot`, `Kbd`, `QuantityField` and `AvatarGroup` are built from
primitives because M3 has nothing to start from.
AGENTS.md's *Building a component* is the rule.

**Colour, shape, border and padding go through M3's own `*Colors` and `*Defaults`.** A `Style` is
for what M3 has no parameter for:

```kotlin
// The 5 × 7 matrix, resolved once, in the shape M3 accepts.
fun buttonColors(variant: ButtonVariant, color: ButtonColor): ButtonColors = …

// What is left: the press giving under the finger.
val buttonStyle: Style = Style {
    pressed  { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.97f) } }
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
Card(style = StrangeTheme.styles.card then { alpha(0.6f) })
```

## Adding a component

0. **Check Material 3 first.** If M3 has the component, wrap it — `material3-compose`'s
   `references/components.md` is the list. Rebuilding one is a decision to justify in the KDoc, not
   a default.
1. **The colours and the style**, in `src/<area>/<Name>Styles.kt`. Whatever M3 can express goes in a
   function returning its `*Colors` / `*Elevation` / `BorderStroke`; whatever it cannot goes in a
   `Style`, reading tokens through `StyleScope` (`scheme`, `shapes`, `spacing`, `motion`)
   and putting interaction states in `pressed` / `hovered` / `disabled` blocks with `animate { }`
   *inside* them. Mind the axis: `spatial` overshoots, `effects` does not.
2. **The composable**, in `src/<area>/<Name>.kt`. Its signature carries `modifier`, then the
   semantic parameters, then `style: Style = Style` — no `Color`, `Shape` or `Dp` parameters that
   the style or M3's defaults already own. Slots (`leading`, `trailing`, `content`) are
   `@Composable`, never `iconName: String`.
3. **Register its story in the same change**, in `examples/material-demo/md-catalog/src/stories/`. The
   catalogue is never caught up with afterwards.
4. **Document it in `docs/components.md`** and tick its box in `docs/roadmap.md`, in that same
   change.
5. **Spec whatever does not need a renderer**: token derivation, colour derivation, enum totality,
   path data. A claim about *pixels* — a hover that has to be visible, a collapsed button that has
   to be round — is measured in pixels instead: `ImageComposeScene` renders a composable to a
   bitmap with no window, in milliseconds, on a headless host. Those specs live in `test@jvm/`
   (`ChipHoverTest`, `ResponsiveButtonTest`) because skiko's native library comes from
   `$compose.desktop.currentOs`, which is jvm-only. Both were written because something looked
   right and was not.

## Platforms

`[ jvm, android, iosArm64, iosSimulatorArm64 ]`. `iosX64` is not a valid target — the Compose
artifacts do not publish for it.

**A green build on this Linux host says nothing about the Apple targets**: they are resolved into
the dependency graph but silently skipped at compile time. Only a macOS host or CI can break on
them. The `compose-multiplatform` skill records this and the rest of what the toolchain actually
does here.

## Publishing

`com.strange:stx-material:0.1.0`, like every other `libs/*` module — but a `kmp/lib` publishes one
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
| [`../../examples/material-demo/`](../../examples/material-demo) | The catalogue: `./kotlin run -m md-desktop` |

[material-kolor]: https://github.com/jordond/MaterialKolor
