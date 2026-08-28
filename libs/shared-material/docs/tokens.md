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

The whole palette follows one `seed: Color` given to `StrangeTheme`. Changing the seed repaints
every component; no component holds a colour of its own.

## Spacing

`StrangeSpacing` — an eight-step scale, and nothing between the steps.

| | `none` | `xxs` | `xs` | `sm` | `md` | `lg` | `xl` | `xxl` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| dp | 0 | 2 | 4 | 8 | 16 | 24 | 32 | 48 |

`scaledBy(factor)` returns the whole scale multiplied — the hook for a density preference, applied
once at `StrangeTheme` rather than per component.

## Radii

`StrangeRadii` derives everything from a single `base`, the way `--radius` does in CSS. One number
moves the roundness of the entire product.

| | derivation | at the default `base = 10.dp` |
| --- | --- | --- |
| `none` | `0.dp` | 0 |
| `sm` | `base - 6.dp`, floored at 0 | 4 |
| `md` | `base - 3.dp`, floored at 0 | 7 |
| `lg` | `base` | 10 |
| `xl` | `base + 6.dp` | 16 |
| `xxl` | `base + 18.dp` | 28 |
| `full` | `1000.dp`, **not** derived | 1000 |

`full` is a constant because a pill is not a big rounded rectangle — it must stay a pill at any
base. `toShapes()` maps the scale onto M3's five `Shapes` slots, which is what makes a plain M3
component inside `StrangeTheme` match the ones here.

## Elevation

`StrangeElevation` — `none`, `low`, `medium`, `high`. Only `CardVariant.Elevated` currently spends
one; the scale exists so the surfaces in phase 4 do not each invent a number.

## Motion

`StrangeMotion` is where every duration and easing in the library lives. **No component writes
`tween(300)`.**

| Duration | ms | For |
| --- | --- | --- |
| `instant` | 80 | A press responding under the finger |
| `quick` | 140 | Hover, focus, a small colour change |
| `standard` | 240 | Content arriving or leaving |
| `slow` | 400 | A large surface, a full-screen move |

| Easing | Curve | For |
| --- | --- | --- |
| `emphasized` | `(0.2, 0, 0, 1)` | The default — anything the eye follows |
| `emphasizedDecelerate` | `(0.05, 0.7, 0.1, 1)` | Something arriving and settling |
| `emphasizedAccelerate` | `(0.3, 0, 0.8, 0.15)` | Something leaving |
| `standardEasing` | `(0.2, 0, 0, 1)` | Utility moves |

`spec(duration, easing)` builds the `tween`, and `quickSpec()` / `standardSpec()` / `slowSpec()`
name the three combinations that come up.

**`enabled = false` sets every duration to zero rather than removing the animation.** The states,
the transitions and the composables are identical either way — only the clock stops. That is what
lets a reduced-motion preference, or a screenshot test, be one flag at the theme.

`Transitions` names the `EnterTransition` / `ExitTransition` pairs built from these: `fade` /
`fadeAway`, `riseIn` / `sinkOut`, `popIn` / `popOut`, `widen` / `narrow`, `expand` / `collapse`.

## Styles

`StrangeTheme.styles` (from `com.strange.material.style`) is every component default in one place —
`button(variant, color)`, `iconButton(…)`, `card(variant, interactive)`, `listTile(interactive)`,
`badge(tone)`, `alert(tone)`, `chip`.

Nothing is needed from it to *use* the library: each component already applies its own. It is the
seam for changing one, and restating the default before editing it is what keeps a one-off from
drifting away from the rest of the screen.

```kotlin
Card(style = StrangeTheme.styles.card(CardVariant.Elevated) then { border(2.dp, scheme.primary) })
```

It is a plain `object` reached through an extension property, not a composition local, because a
`Style` reads its tokens when it is *applied*, not when it is written. `then` is a top-level infix
in `androidx.compose.foundation.style` and needs its own import.
