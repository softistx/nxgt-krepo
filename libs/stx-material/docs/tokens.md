# Tokens

What a component is allowed to say instead of a number. Every token is reachable two ways — from a
composable through `StxTheme`, and from inside a `Style` through `StyleScope` — and both read
the same composition local, so they can never disagree.

```kotlin
@Composable fun Something() = Box(Modifier.padding(StxTheme.spacing.md))

val somethingStyle = Style { contentPadding(spacing.md) }
```

**This is the file a new token is documented in.** A token that exists in code and not here is not
finished.

## Colour

`StxColors` holds the Material 3 `ColorScheme` and adds the semantic roles M3 does not define.

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
that does nothing. `stxColors(scheme, isDark)` then adds the semantic roles to whichever scheme
arrived — the extra roles are not tied to the seed path.

No component holds a colour of its own, so changing the scheme repaints all of them.

## Spacing

`StxSpacing` — an eight-step scale, and nothing between the steps.

| | `none` | `xxs` | `xs` | `sm` | `md` | `lg` | `xl` | `xxl` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| dp | 0 | 2 | 4 | 8 | 16 | 24 | 32 | 48 |

`scaledBy(factor)` returns the whole scale multiplied — the hook for a density preference, applied
once at `StxTheme` rather than per component.

## Shapes and elevation — Material 3's, not ours

Neither is a token here, and that is the point: `MaterialTheme.shapes` already ships **eight**
slots, and every component already carries its elevation on its own `*Defaults`. A second ladder
beside either one buys nothing and gives the two somewhere to disagree.

```kotlin
Surface(shape = MaterialTheme.shapes.medium) { … }   // a composable
Style { shape(shapes.medium) }                       // inside a Style
Card(elevation = CardDefaults.elevatedCardElevation())
```

The eight slots are `extraSmall`, `small`, `medium`, `large`, `largeIncreased`, `extraLarge`,
`extraLargeIncreased` and `extraExtraLarge` — the last three arrived with Material 3 expressive and
are gated behind `ExperimentalMaterial3ExpressiveApi`. A hand-written `Shapes(…)` fills only five
and leaves those three on their defaults, which is a rounding that looks almost right; passing a
whole `Shapes` to `StxTheme` is how a product changes them.

There is no `full`: a pill is `CircleShape`, the same 50% corner M3 uses and reachable from Kotlin,
unlike `ShapeDefaults.CornerFull`.

`shapes` is mirrored onto a `CompositionLocal` for one reason only — a `StyleScope` is not a
composable and `MaterialTheme.localMaterialTheme` is `internal` to Kotlin, so a `Style` cannot ask
M3 directly. It is the *same instance* the theme hands `MaterialExpressiveTheme`; a composable
reads `MaterialTheme.shapes` and never touches the mirror.

## Motion

**Motion is Material 3's, not ours.** `StxMotion` holds a `MotionScheme` — the one
`StxTheme` hands `MaterialExpressiveTheme` — so a plain M3 component and one of ours animate
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
what `Transitions` does per half. `StxMotionTest` pins the fact underneath: every spatial spec
is damped below 1, every effects spec at exactly 1.

`StxMotion` is *held*, not read from the composition, because a `Style` block is not a
composable scope — it runs at apply time.

**Which scheme.** `MotionScheme.expressive()` is the default: springier, allowed to overshoot.
`MotionScheme.standard()` settles instead. Passing one to `StxTheme` changes every animation in
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

`StxTheme.styles` (from `com.softistx.material.style`) is every component default in one place —
`button`, `card`, `chip`, `listTile`, `field`, `alert(tone)`.

There is less there than there once was, and that is the point: colour, shape, border, padding and
elevation moved into Material 3's own `*Colors` and `*Defaults` when the components were rebuilt on
M3. What is left is what M3 has no parameter for — the press scale, the disabled alpha, and the
alert's whole appearance, because M3 has no banner.

Nothing is needed from it to *use* the library: each component already applies its own. It is the
seam for *adding* to one.

```kotlin
Card(style = StxTheme.styles.card then { alpha(0.6f) })
```

To change a **colour**, pass Material 3's `*Colors` instead. A `background()` in a style block paints
behind a surface M3 has already painted, so it either does nothing or hides the state M3 was
showing — which is exactly how the chip lost its selected container the first time.

It is a plain `object` reached through an extension property, not a composition local, because a
`Style` reads its tokens when it is *applied*, not when it is written. `then` is a top-level infix
in `androidx.compose.foundation.style` and needs its own import.
