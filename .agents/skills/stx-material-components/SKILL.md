---
name: stx-material-components
description: Create or modernise a component in libs/ui/stx-material — wrap Material 3 Expressive, dress it with a Style, prove it with pixel and semantics specs, then story, docs, changeset. Use before adding, restyling or reviewing any stx-material component.
---

# Building a stx-material component

The order of work. AGENTS.md's *Building a component* and *Styling a component* hold the rules; `material3-compose` lists what M3 offers; `styles` teaches the Styles API and says it does not cover Material components. This skill joins them: **M3 supplies the control, a `Style` supplies what M3 cannot express, a pixel spec proves it.** The look is Expressive by default — shape morph, springs, emphasised type — not an opt-in.

Read first: `libs/ui/stx-material/docs/components.md` (what exists), `docs/tokens.md` (what a component may say instead of a number), and one neighbour with its `*Styles.kt` — `components/button/Button.kt`, `components/content/Card.kt`. A complete worked component is `references/worked-example.md`.

## 1. Place it

- A folder under `src/components/` (`button`, `form`, `navigation`, `layout`, `feedback`, `content`, `surface`, `media`, `data`, `datetime`), or `src/foundation/` for theme, style, motion, layout, icon, text. `Name.kt` holds the composable; its `Style` and `*Colors`/`*Elevation` resolvers go in the family's existing `*Styles.kt` or a new `<Name>Styles.kt`.
- Extend before adding: a defaulted parameter on an existing component beats a sibling.
- `src@jvm`, `src@android`, `src@ios` hold only what truly differs per platform.

Done when you can name the folder and the existing component you checked.

## 2. Check Material 3 first

1. Search `.agents/skills/material3-compose/references/components.md` and `defaults.md`.
2. The list is read off the jar, so it is complete. For a parameter list, open the `-sources.jar` beside the resolved `material3-desktop-1.12.0-alpha03.jar` in the toolchain cache, or use `javap`.
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
    MaterialCard(
        onClick,
        modifier.styledBy(style, enabled, choiceTileStyle, interactionSource),
        interactionSource = interactionSource,
    ) { … }
}

internal val choiceTileStyle: Style =
    rowPress then
        Style {
            disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
        }
```

- `style: Style = Style` is the only styling parameter, the last parameter (or the last before one trailing lambda), applied last so the caller wins. Interactive components all take it; an inert one (a divider, `Kbd`) says why it does not.
- Read tokens through `foundation/style/StyleTokens.kt` (`scheme`, `shapes`, `spacing`, `colors`, `motion`); the default is named in `StxStyles` and the docs.
- States are declared: `pressed`, `hovered`, `focused`, `disabled`, `selected`, `checked`, with `animate(spec) { … }` inside. Build the state with `Modifier.styledBy(style, enabled, base, interactionSource, selected)` (`foundation/style/Pressable.kt`), never an inline `rememberUpdatedStyleState` + `styleable`; a part that is only a press target when clickable uses `pressable(base, style, onClick)`, and `pressBase(base, onClick)` gives the base only then. Reuse `rowPress` (0.99) and `compactPress` (0.97) for the press instead of writing the block again. No `animate*AsState` for an interaction state.
- Speed by axis: `motion.spatial(..)` for scale, offset, size; `motion.effects(..)` for alpha and colour. Detail in `compose-motion`.
- Press scale follows the family: 0.99 for cards and rows, 0.97 for chips and navigation items. **A control that morphs its shape (`shapes =`, section 5) gets no `scale`**: two give-under-the-finger effects fight.
- A `background()` or `shape()` in a Style over an M3 container paints twice; it belonged in `*Colors` or `shape =`. A border drawn by the Style needs the same `shape(..)` as the container.

Done when `Name.kt` holds no colour literal, the Styles file holds nothing M3 has a parameter for, and `StxStyles` (`components/StxStyles.kt`) names the default (`val choiceTile: Style get() = choiceTileStyle`), with the `*Style` val itself `internal`.

## 5. Reach for Expressive

`references/expressive-vocabulary.md` has every API, verified against 1.12.0-alpha03. The decisions:

- **Button, icon button, toggle, FAB, split button: shape morph** through the `shapes =` overload (`ButtonDefaults.shapes()`); no press `scale` on these (section 4).
- **A choice among a few options: a connected group** of `ToggleButton`s with `ButtonGroupDefaults.connected*ButtonShapes()`, not a custom segmented row.
- **Shapes:** `largeIncreased` (20 dp) for cards and containers, `extraLargeIncreased` (32 dp) for sheets and hero blocks, `CircleShape` for pills. `MaterialShapes.*.toShape()` for a decorative mask such as an avatar or a step marker.
- **Type:** the `*Emphasized` styles for the one line that should win (a hero title, a metric). Go through `Typography` and its `emphasized` flag where it exists; never `MaterialTheme.typography` in a component.
- **Bars and waits:** `MediumFlexibleTopAppBar`/`LargeFlexibleTopAppBar`; `LoadingMark` (M3's `LoadingIndicator`) when the wait is the content, `Progress` when it is attached to a control.
- Motion comes only from `StxMotion`; `compose-motion` says how.

## 6. The quality bar

A component looks modern here when it meets all of these; `references/visual-quality.md` explains each.

1. One hierarchy from the type scale: one dominant line, the rest regular, secondary text at `Emphasis.Medium` (`Subtle`, 0.72, passes AA, for non-essential text).
2. One accent. Meaning comes from `Tone` pairs, never a raw colour.
3. Tonal surfaces over borders; elevation only when a thing floats.
4. The spacing scale (`StxTheme.spacing`), generous inside, never a bare `dp` number.
5. A 48 dp target and a role: `compose-accessibility`.
6. Motion that explains a change, then stops.
7. Empty, loading and error states exist (`EmptyState`, `Skeleton`, `UiState`, `Alert`).
8. Correct in dark mode, at 2× font scale, and with motion off.

Done when you have rendered it, light and dark, at 1× and at 2× font scale, to PNG in the scratchpad with `RenderProbe.savePng(path)` (`test@jvm/RenderProbe.kt`), opened the pictures, and can name the dominant line, the one accent and the surfaces. Wrap the content in `CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f))` for the large-type picture.

## 7. Prove it

Under `libs/ui/stx-material/` (pattern in `references/worked-example.md`):

- **Pixel spec** in `test@jvm/components/<family>/`: `renderProbe { … }`, `Modifier.probed(NodeBounds())`, `settle()`, `press(node)`, `pixelAt`, `isTransparent`, `ink`. Assert what you changed: the corner is clear (shape), pressed ink is smaller (give), selected differs from rest (tone).
- **Semantics spec** with `runComposeUiTest`, modelled on `ClickableRolesTest`: role, click action, selection or state, `assertHeightIsAtLeast(48.dp)`. M3's clickable `Card` sets no role: the component adds `Role.Button` (or `RadioButton` for a single choice), and the spec asserts it.
- **Story** in the same change: `story("Name") { knobs -> … }` in `examples/material-demo/md-catalog/src/stories/components/<family>/…Stories.kt` (a new group is also listed in `Catalog.kt`). The catalogue consumes the *published* library, so run `./kotlin publish mavenLocal -m stx-material --non-transitive` before `./kotlin build -m md-catalog`. `EveryStoryRendersTest` renders each story.
- **Docs:** a row in `docs/components.md` (component, parameters, story id); a token in `docs/tokens.md`; the box ticked in `docs/roadmap.md`.
- **Changeset** naming `stx-material`: a new component or defaulted parameter is `minor` in 0.x; a look-only change is `patch`; a source break (removed or renamed parameter, a new required one) needs the owner and a `!` commit. Specs or docs only: `bun changeset --empty`.
- **Green bar:** `./kotlin build -m stx-material -p jvm` and `./kotlin test -m stx-material -p jvm`, each redirected to a file with `$?` echoed; then `ktlint -F --relative "**/*.kt" "!build/**"`.

## 8. Checklist and neighbours

`references/component-checklist.md` is the pre-PR list. Accessibility: `compose-accessibility`. Motion: `compose-motion`. Recomposition and lazy lists: `jetpack-compose-performance`. Platform and test setup: `compose-multiplatform`.
