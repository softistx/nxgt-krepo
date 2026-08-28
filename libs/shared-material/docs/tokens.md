# Tokens

What a component is allowed to say instead of a number. Every token is reachable two ways — from a
composable through `StrangeTheme`, and from inside a `Style` through `StyleScope` — and both read
the same composition local, so they can never disagree.

```kotlin
@Composable fun Something() = Box(Modifier.padding(StrangeTheme.spacing.md))

val somethingStyle = Style { contentPadding(spacing.md) }
```

**This is the file a new token is documented in.** A token that exists in code and not here is not
finished.

## Colour

`StrangeColors` holds the Material 3 `ColorScheme` and adds the semantic roles M3 does not define.

| Role | Members | Where it comes from |
| --- | --- | --- |
| The 48 M3 roles | `colors.scheme.primary`, `.surfaceContainerHigh`, … | `dynamicColorScheme(seed, isDark)` |
| `success` | `success`, `onSuccess`, `successContainer`, `onSuccessContainer` | derived from its own seed |
| `info` | same four | derived from its own seed |
| `warning` | same four | derived from its own seed |
| `error` | same four | **delegated to `scheme`** — never re-derived |

`error` is delegated on purpose: a second red derived from a second seed is a second red, and no
design system wants two. `colors.tone(Tone.Error)` and `MaterialTheme.colorScheme.error` are the
same colour.

`Tone` (`Success` / `Info` / `Warning` / `Error`) is how a component asks for one of the four
without naming twelve colours: `colors.tone(tone)` returns a `ToneColors` — `main`, `onMain`,
`container`, `onContainer`. `StatusBadge` and `Alert` take a `Tone` for exactly this reason.

**Where the scheme comes from is a platform decision, and only that decision is
platform-specific.** `platformColorScheme(seed, isDark, dynamicColor)` answers with the user's
wallpaper palette on Android 12+ and with the seed everywhere else; `supportsDynamicColor` says
which, so a settings screen can decide whether to *offer* the choice rather than showing a switch
that does nothing. `strangeColors(scheme, isDark)` then adds the semantic roles to whichever scheme
arrived — the extra roles are not tied to the seed path.

No component holds a colour of its own, so changing the scheme repaints all of them.

## Spacing

`StrangeSpacing` — an eight-step scale, and nothing between the steps.

| | `none` | `xxs` | `xs` | `sm` | `md` | `lg` | `xl` | `xxl` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| dp | 0 | 2 | 4 | 8 | 16 | 24 | 32 | 48 |

`scaledBy(factor)` returns the whole scale multiplied — the hook for a density preference, applied
once at `StrangeTheme` rather than per component.

## Radii

`StrangeRadii` derives everything from a single `base`, the way `--radius` does in CSS — but the
rungs are **Material 3's own eight**, shifted so that `medium`, M3's middle slot and the one most
components reach for, lands on `base`. One number moves the roundness of the entire product,
including the three slots M3 added in 1.11 that a hand-written `Shapes` silently leaves behind.

| slot | at the default `base = 12.dp` | derivation |
| --- | --- | --- |
| `extraSmall` | 4 | `4.dp + shift` |
| `small` | 8 | `8.dp + shift` |
| `medium` | 12 | **`base`** |
| `large` | 16 | `16.dp + shift` |
| `largeIncreased` | 20 | `20.dp + shift` |
| `extraLarge` | 28 | `28.dp + shift` |
| `extraLargeIncreased` | 32 | `32.dp + shift` |
| `extraExtraLarge` | 48 | `48.dp + shift` |

`shift` is `base - 12.dp`, and every rung is floored at 0. At the default the shapes are *exactly*
M3's, which is what makes a plain M3 component inside `StrangeTheme` match the ones here.

There is no `full`: a pill is `CircleShape`, which is the same 50% corner M3 uses and is reachable
from Kotlin, unlike `ShapeDefaults.CornerFull`.

The names are M3's too, and that is deliberate. A component asks `MaterialTheme.shapes.medium` — or
`shapes.medium` inside a `Style` — rather than reading `radii` directly. `StrangeRadii` is the
*input* to the theme, not a second vocabulary.

## Elevation

`StrangeElevation` — `flat` 0, `raised` 1, `floating` 3, `overlay` 6, `modal` 12 dp, named by what a
surface *is* rather than by how far it is lifted.

This one is genuinely ours: Material 3 has no theme-level elevation the way it has `colorScheme`,
`typography` and `shapes`, only per-component types like `CardElevation`. The **values** are not
ours — each is one of M3's six levels, so a surface raised through this scale sits exactly where a
plain M3 component raised through its own `*Defaults` sits. Level 4 (8 dp) has no name here because
nothing has needed one.

## Motion

**Motion is Material 3's, not ours.** `StrangeMotion` holds a `MotionScheme` — the one
`StrangeTheme` hands `MaterialExpressiveTheme` — so a plain M3 component and one of ours animate
with the same curves. No component writes `tween(300)`, and none holds an easing of its own.

M3 splits motion along an axis worth keeping:

| Axis | For | Behaviour |
| --- | --- | --- |
| `spatial(speed)` | Position, size, rotation — anything the eye tracks | A spring; **may overshoot** |
| `effects(speed)` | Colour, alpha, elevation | Must land exactly on the target — an overshooting colour was never in the palette |

Each takes a `MotionSpeed`: `Fast`, `Default` (the default argument), `Slow`. There is no
`instant` — a change that should not be seen is not an animation.

Getting this wrong is subtle rather than loud: giving a fade and a slide the same curve, which is
what this library did before it was built on `MotionScheme`, makes a combined transition finish in
two stages. `Transitions` now picks per half.

```kotlin
pressed { animate(motion.spatial(MotionSpeed.Fast)) { scale(0.97f) } }
disabled { animate(motion.effects()) { alpha(DISABLED_ALPHA) } }
```

**A spatial spec overshoots, so what it drives has to tolerate leaving its range.** This is not a
style point, it is a crash: the catalogue's own motion story drove `Modifier.padding` from a
spatial spring and died on *Padding must be non-negative* the first time the square came back.
`Modifier.offset` takes it, `padding` and `size` do not, and an alpha past 1 is silently clamped.
When the sink cannot take an overshoot the value belongs on `effects` — or the two belong on
separate animations, which is what `Modifier.animateStagger` does (spatial rise, effects fade) and
what `Transitions` does per half. `StrangeMotionTest` pins the fact underneath: every spatial spec
is damped below 1, every effects spec at exactly 1.

`StrangeMotion` is *held*, not read from the composition, because a `Style` block is not a
composable scope — it runs at apply time.

**Which scheme.** `MotionScheme.expressive()` is the default: springier, allowed to overshoot.
`MotionScheme.standard()` settles instead. Passing one to `StrangeTheme` changes every animation in
the tree, this library's and Material 3's alike. Both are singletons, so two default themes compare
equal and installing one is not a recomposition.

**`enabled = false` collapses every spec to `snap()`** rather than removing the animation. The
states, the transitions and the composables are identical either way — only the clock stops. That
is what lets a reduced-motion preference, or a screenshot test, be one flag at the theme. It stops
*this library's* motion; Material 3's own components keep their built-in animations, because a
`MotionScheme` has no null.

`Transitions` names the `EnterTransition` / `ExitTransition` pairs built from these: `fade` /
`fadeAway`, `riseIn` / `sinkOut`, `popIn` / `popOut`, `widen` / `narrow`, `expand` / `collapse`.
Exits are one speed faster than their entrances throughout — something leaving should get out of
the way, not be watched.

`Modifier.shimmer()` is the exception that proves the rule: a sweep is a loop rather than a state
change, so it names its own cadence. `MotionScheme` has no spec for something that never settles.

## Styles

`StrangeTheme.styles` (from `com.strange.material.style`) is every component default in one place —
`button`, `card`, `chip`, `listTile`, `field`, `alert(tone)`.

There is less there than there once was, and that is the point: colour, shape, border, padding and
elevation moved into Material 3's own `*Colors` and `*Defaults` when the components were rebuilt on
M3. What is left is what M3 has no parameter for — the press scale, the disabled alpha, and the
alert's whole appearance, because M3 has no banner.

Nothing is needed from it to *use* the library: each component already applies its own. It is the
seam for *adding* to one.

```kotlin
Card(style = StrangeTheme.styles.card then { alpha(0.6f) })
```

To change a **colour**, pass Material 3's `*Colors` instead. A `background()` in a style block paints
behind a surface M3 has already painted, so it either does nothing or hides the state M3 was
showing — which is exactly how the chip lost its selected container the first time.

It is a plain `object` reached through an extension property, not a composition local, because a
`Style` reads its tokens when it is *applied*, not when it is written. `then` is a top-level infix
in `androidx.compose.foundation.style` and needs its own import.
