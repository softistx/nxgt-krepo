---
name: stx-material-components
description: Create or modernise a component in libs/ui/stx-material — wrap Material 3 Expressive, dress it with a Style, prove it with pixel and semantics specs, then story, docs, changeset. Use before adding, restyling or reviewing any stx-material component.
---

# Building a stx-material component

The order of work. AGENTS.md's *Building a component* and *Styling a component* hold the rules; `material3-compose` lists what M3 offers; `styles` teaches the Styles API and says it does not cover Material components. This skill joins them: **M3 supplies the control, a `Style` supplies what M3 cannot express, a pixel spec proves it.** The look is Expressive by default — shape morph, springs, emphasised type — not an opt-in.

Read first: `libs/ui/stx-material/docs/components.md` (what exists), `docs/tokens.md` (what a component may say instead of a number), and one neighbour with its `*Styles.kt` — `components/button/Button.kt`, `components/content/Card.kt`. A complete worked component is `references/worked-example.md`.

## 1. Place it

- A folder under `src/components/` (`button`, `form`, `navigation`, `layout`, `feedback`, `content`, `surface`, `media`, `data`, `datetime`), or `src/foundation/` for theme, style, motion, layout, icon, text. Two files per component: `Name.kt` and `NameStyles.kt` (the `Style` plus its `*Colors`/`*Elevation` resolvers).
- Extend before adding: a defaulted parameter on an existing component beats a sibling.
- `src@jvm`, `src@android`, `src@ios` hold only what truly differs per platform.

Done when you can name the folder and the existing component you checked.

## 2. Check Material 3 first

1. Search `.agents/skills/material3-compose/references/components.md` and `defaults.md`.
2. That list is generated from public names, and a function taking `Color` or `Dp` has a mangled name, so it can be missing (`ContainedLoadingIndicator`, `TopAppBar` and `Scaffold` are). Confirm in the `-sources.jar` beside the resolved `material3-desktop-1.12.0-alpha03.jar` in the toolchain cache, or with `javap`.
3. M3 has it: wrap it. M3 has nothing: build from primitives, and say so in the KDoc (`Alert`, `EmptyState`, `Skeleton`).

Done when you can name the M3 function and its `*Defaults` object.

## 3. Wrap through `*Defaults`

- Colour: `CardDefaults.cardColors(...)` and the other `*Colors` factories. Shape: `*Defaults.shapes()` or `MaterialTheme.shapes.<slot>`. Elevation, padding and sizes: the same `*Defaults`. Our tokens compute the argument; M3 paints.
- Rebuilding from `Row` + `Modifier.background` throws away the ripple, the 48 dp target, the semantics and RTL.
- The experimental gates are opted in module-wide in `module.yaml`; add no per-file `@OptIn`. An API annotated plain `Material3ExpressiveApi` (no `Experimental`) is `@RestrictTo` library-internal: avoid it.

## 4. The look is a Style

```kotlin
@Composable
fun Tile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    MaterialCard(onClick, modifier.styleable(styleState, tileStyle, style), interactionSource = interactionSource) { … }
}

internal val tileStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.98f) } }
        disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
    }
```

- `style: Style = Style` is the only styling parameter, after `enabled`, applied last so the caller wins. Interactive components all take it; an inert one (a divider, `Kbd`) says why it does not.
- Read tokens through `foundation/style/StyleTokens.kt` (`scheme`, `shapes`, `spacing`, `colors`, `motion`); the default is named in `StxStyles` and the docs.
- States are declared: `pressed`, `hovered`, `focused`, `disabled`, `selected`, `checked`, with `animate(spec) { … }` inside. Feed `selected`/`checked` through `rememberUpdatedStyleState` (`it.isSelected = selected`). No `animate*AsState` for an interaction state.
- Speed by axis: `motion.spatial(..)` for scale, offset, size; `motion.effects(..)` for alpha and colour. Detail in `compose-motion`.
- A `background()` or `shape()` in a Style over an M3 container paints twice; it belonged in `*Colors` or `shape =`. A border drawn by the Style needs the same `shape(..)` as the container.

Done when `Name.kt` holds no colour literal and `NameStyles.kt` holds nothing M3 has a parameter for.

## 5. Reach for Expressive

`references/expressive-vocabulary.md` has every API, verified against 1.12.0-alpha03. The decisions:

- **Button, icon button, toggle, FAB, split button: shape morph** through the `shapes =` overload (`ButtonDefaults.shapes()`). A morphing button gets no `scale` in its Style; scale is for everything else interactive (cards 0.98–0.99, rows, chips).
- **A choice among a few options: a connected group** of `ToggleButton`s with `ButtonGroupDefaults.connected*ButtonShapes()`, not a custom segmented row.
- **Shapes:** `largeIncreased` (20 dp) for cards and containers, `extraLargeIncreased` (32 dp) for sheets and hero blocks, `CircleShape` for pills. `MaterialShapes.*.toShape()` for a decorative mask such as an avatar or a step marker.
- **Type:** the `*Emphasized` styles for the one line that should win (a hero title, a metric). Go through `Typography` and its `emphasized` flag where it exists; never `MaterialTheme.typography` in a component.
- **Bars and waits:** `MediumFlexibleTopAppBar`/`LargeFlexibleTopAppBar`; `LoadingIndicator` for an indeterminate wait. `Progress` stays linear.
- Motion comes only from `StxMotion`; `compose-motion` says how.

## 6. The quality bar

A component looks modern here when it meets all of these; `references/visual-quality.md` explains each.

1. One hierarchy from the type scale: one dominant line, the rest regular and `Emphasis.Medium`/`Subtle`.
2. One accent. Meaning comes from `Tone` pairs, never a raw colour.
3. Tonal surfaces over borders; elevation only when a thing floats.
4. The spacing scale (`StxTheme.spacing`), generous inside, never a bare `dp` number.
5. A 48 dp target and a role: `compose-accessibility`.
6. Motion that explains a change, then stops.
7. Empty, loading and error states exist (`EmptyState`, `Skeleton`, `UiState`, `Alert`).
8. Correct in dark mode, at 2× font scale, and with motion off.

## 7. Prove it

Under `libs/ui/stx-material/` (pattern in `references/worked-example.md`):

- **Pixel spec** in `test@jvm/components/<family>/`: `renderProbe { … }`, `Modifier.probed(NodeBounds())`, `settle()`, `press(node)`, `pixelAt`, `isTransparent`, `ink`. Assert what you changed: the corner is clear (shape), pressed ink is smaller (give), selected differs from rest (tone).
- **Semantics spec** with `runComposeUiTest`, modelled on `ClickableRolesTest`: role, click action, selection or state, `assertHeightIsAtLeast(48.dp)`. M3's clickable `Card` sets no role; add `Role.Button`.
- **Story** in the same change: `story("Name") { knobs -> … }` in `examples/material-demo/md-catalog/src/stories/components/<family>/…Stories.kt` (a new group is also listed in `Catalog.kt`). The catalogue consumes the *published* library, so run `./kotlin publish mavenLocal -m stx-material --non-transitive` before `./kotlin build -m md-catalog`. `EveryStoryRendersTest` renders each story.
- **Docs:** a row in `docs/components.md` (component, parameters, story id); a token in `docs/tokens.md`.
- **Changeset** naming `stx-material`: a new component or defaulted parameter is `minor` in 0.x; a look-only change is `patch`; a source break (removed or renamed parameter, a new required one) needs the owner and a `!` commit. Specs or docs only: `bun changeset --empty`.
- **Green bar:** `./kotlin build -m stx-material -p jvm` and `./kotlin test -m stx-material -p jvm`, each redirected to a file with `$?` echoed; then `ktlint -F --relative "**/*.kt" "!build/**"`.

## 8. Checklist and neighbours

`references/component-checklist.md` is the pre-PR list. Accessibility: `compose-accessibility`. Motion: `compose-motion`. Recomposition and lazy lists: `jetpack-compose-performance`. Platform and test setup: `compose-multiplatform`.
