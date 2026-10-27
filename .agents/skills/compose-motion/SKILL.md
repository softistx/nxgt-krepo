---
name: compose-motion
description: Motion for Compose Multiplatform with Material 3 Expressive — spatial vs effects specs, StxMotion, Style animate, shape morph, enter/exit, stagger, reduced motion. Use when adding or reviewing any animation in stx-material.
---

# Motion in Compose Multiplatform

Motion here answers one question: what changed, where from, or what did my touch do. Anything else is decoration. `references/INDEX.md` lists the fetched Android pages (animation APIs, `MotionScheme`, testing); this file is the house decisions on top of them.

## Two axes, never one curve

M3's `MotionScheme` (installed by `MaterialExpressiveTheme`, which `StxTheme` wraps) splits motion into:

- **Spatial**: anything that moves or changes size or shape. A spring damped below 1, so it overshoots and settles. Default damping 0.8, stiffness 380; fast 0.6 / 800; slow 0.8 / 200 (expressive).
- **Effects**: colour, alpha, elevation. Critically damped (1.0), lands exactly on target.

`MotionScheme.standard()` damps at 0.9 and is stiffer: calmer, for dense tools. Expressive is the default of this library; pass `motion = StxMotion(MotionScheme.standard())` to `StxTheme` to calm a whole app.

`StxTheme.motion` (`StxMotion`) is the only source of specs in this library:

```kotlin
motion.spatial<Float>(MotionSpeed.Fast)   // scale, offset, size, rotation
motion.effects<Color>()                    // colour, alpha  (Default speed)
```

`MotionSpeed` is `Fast`, `Default`, `Slow`. Fast for press feedback and exits, Default for most things, Slow for large surfaces. There is no `instant`: a change that should not be seen is not an animation. No `tween(...)`, no easing curves, no literal durations in a component.

**Overshoot trap.** A spatial spring passes its target and returns. `Modifier.offset` and `graphicsLayer` tolerate that; `padding`, `size` and `Constraints` throw (*Padding must be non-negative*); alpha past 1 is clamped silently. If the sink rejects it, put that value on `effects`, or split it into two animations. `animateStagger` and `Transitions` do this.

## Interaction states: Style `animate`

```kotlin
internal val tileStyle: Style =
    Style {
        pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.99f) } }
        disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
    }
```

- `motion` inside a Style block is a held reference (a Style is not a composable scope); `StxTheme` provides it.
- Scale and offset use `spatial`, colour and alpha use `effects`. No `animate*AsState` for pressed, hovered, focused, selected.
- Press scale follows the family (0.99 cards and rows, 0.97 chips, buttons, navigation items); a morphing control gets none: `stx-material-components` section 4.

## Shape morph

M3 buttons morph between `shape`, `pressedShape` and, for toggles, `checkedShape`. Use the `shapes =` overload with `ButtonDefaults.shapes()`, `IconButtonDefaults.shapes()`, `ToggleButtonDefaults.shapes()`, `ListItemDefaults.shapes()`. `Button` drives the morph with the effects spec (no bounce); `ToggleButton` with the fast spatial one. It does not combine with `shape =`. `stx-material-components/references/expressive-vocabulary.md` lists every overload.

## Enter and exit

Use `Transitions` (in `foundation/motion`) rather than writing `fadeIn(tween(..))`: `fade`, `fadeAway`, `riseIn`, `sinkOut`, `popIn`, `popOut`, `widen`, `narrow`, `expand`, `collapse`. Each half picks its own axis (the fade on effects, the slide or scale on spatial), and exits run one speed faster than entrances: what leaves gets out of the way.

```kotlin
AnimatedVisibility(visible = open, enter = Transitions.riseIn, exit = Transitions.sinkOut) { Panel() }
```

**`AnimatedVisibility(visible = true)` never animates its first appearance** (measured: alpha 1.0 on the first frame, no series). To animate content that is there from the first composition, start from a false state:

```kotlin
val state = remember { MutableTransitionState(false).apply { targetState = true } }
AnimatedVisibility(visibleState = state, enter = Transitions.riseIn) { Banner() }
```

With reduced motion on, the specs snap, so the same code is correct.

`AnimatedContent`: read the transition specs from `StxTheme.motion` outside the `transitionSpec` lambda (the lambda is not composable), and use `SizeTransform` when the size changes, so the container does not jump.

```kotlin
val motion = StxTheme.motion
AnimatedContent(targetState = count, transitionSpec = {
    (slideInVertically(motion.spatial()) { it } + fadeIn(motion.effects())) togetherWith
        (slideOutVertically(motion.spatial(MotionSpeed.Fast)) { -it } + fadeOut(motion.effects(MotionSpeed.Fast)))
}) { Text("$it") }
```

Shared elements (`SharedTransitionLayout`) are in `references/animation-shared-elements.md`; use them for a list-to-detail hand-off, not for decoration.

## Lists and waits

- `Modifier.animateStagger(index)`: a list that arrives in sequence, 40 ms a step, capped at index 8 so item 20 does not wait. The wait is counted in frames of the composition's clock, so tests and headless renders step through it. Use it on first appearance only, not on every recomposition or scroll.
- A wait that is the content: `LoadingMark` (M3's shape-morphing `LoadingIndicator`). Content of known shape: `Skeleton`, which shimmers on its own. A wait attached to a control, or a known fraction: `Progress`.
- Lazy list items: `Modifier.animateItem()` for reorder and insert, with a stable `key`.

## Reduced motion

`motion` is the only motion input of `StxTheme`; M3's scheme derives from it (`enabled = false` installs an internal `SnapMotionScheme`, all six specs `snap()`, because the flag alone leaves M3's morphs running). With no `motion`, `StxTheme` follows the OS: `systemReducesMotion()` (expect/actual) feeds `StxMotion.system(reduceMotion)`. **Pass a `motion` and the OS is not read at all.** `StxThemeProvider` forwards it. The read is taken when the theme is composed, not observed.

Where it is read: Android the animator duration scale; iOS `UIAccessibilityIsReduceMotionEnabled`; desktop follows only `-Dstx.material.reduceMotion=true|false` (absent: not reduced, no process spawned). An app opts in to the OS with `suspend fun readDesktopReducedMotion(): Boolean?` (`defaults` on macOS, `gsettings` on GNOME, `null` on Windows and anything unknown; on `Dispatchers.IO`) and passes it in: `LaunchedEffect(Unit) { reduced = readDesktopReducedMotion() == true }`, then `StxTheme(motion = StxMotion.system(reduced))`. The test suite pins the property to `false` in `test@jvm/io/kotest/provided/ProjectConfig.kt`, so specs do not depend on the host. A component must look complete in its end state, with nothing that only the animation explained.

## What not to animate

Layout on every keystroke or scroll; anything on first load of a dense screen (a table, a form); more than one thing competing for the eye; an infinite loop beside text a user is reading (`shimmer()` belongs to a skeleton, which goes away); error and validation messages (announce them, `compose-accessibility`, and show them at once); a value the user is dragging (follow the finger, then settle).

## Test it

Specs live in `libs/ui/stx-material/test@jvm/`. Use `androidx.compose.ui.test.v2.runComposeUiTest`, take control with `mainClock.autoAdvance = false`, then `mainClock.advanceTimeBy(ms)` and `onRoot().captureToImage()` to read a mid-frame pixel. `StxMotion(enabled = false).spatial<Float>()` and `SnapMotionScheme.fastSpatialSpec<Float>()` are `SnapSpec`s; assert both for reduced motion. `references/animation-testing.md` covers `ComposeTestRule` clocks. A spring is not a duration: assert direction and the settled value, not the midpoint.

Neighbours: `stx-material-components` (workflow and pixel specs), `compose-accessibility` (reduced motion, announcements), `styles` (the Style API itself).
