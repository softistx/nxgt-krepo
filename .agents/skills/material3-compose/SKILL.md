---
name: material3-compose
description: The Material 3 API surface that actually compiles here — colour roles, type scale, shape slots, Defaults objects and every public component — read off the resolved jar rather than the web. Use when building on or theming M3 in stx-material.
---

# Material 3 (Compose Multiplatform)

`libs/stx-material` builds *on top of* Material 3: `StrangeTheme` installs a `MaterialTheme`
underneath so ordinary M3 components and third-party M3 libraries keep working inside it.

## Why this skill has no fetched documentation

`$compose.material3` resolves to **1.11.0-alpha07** while `foundation` and `ui` are `1.11.1` —
Material 3 in Compose Multiplatform is on its own alpha line. The androidx documentation on the web
describes a *different artifact at a different version*, and its component list, colour roles and
shape slots do not all agree with what compiles here.

So `references/` is generated from the jar the build actually resolved:

```bash
python3 .agents/skills/material3-compose/scripts/extract_api.py
```

`api-source.json` records which version the pages describe. Re-run after a
`settings.compose.version` bump; the script reads the newest jar in the toolchain's cache, so run
`./kotlin build -m stx-material` first if the cache is cold.

## What the extraction settles

- **48 colour roles**, and none of them is `success`, `info` or `warning`. M3 has `error` and
  nothing else semantic. That absence is the reason `StrangeColors` exists — it adds those three
  with their `on*` and `*Container` pairs and delegates every other role to the M3 `ColorScheme`
  rather than duplicating it.
- **8 shape slots**, including the expressive `largeIncreased`, `extraLargeIncreased` and
  `extraExtraLarge` that the older M3 documentation does not mention.
- **30 type styles** and **58 `*Defaults` objects**.
- **The Compose Styles API is here, experimental, in `foundation` rather than `material3`.**
  `androidx.compose.foundation.style` ships in Compose Multiplatform: 36 classes at foundation
  1.11.1, 84 at 1.12.0. `Modifier.styleable(StyleState, Style)` exists today, along with `Style`,
  `StyleScope`, `StyleState`, `CombinedStyle`, `StyleAnimations` and the interaction keys, all
  gated by `@ExperimentalFoundationStyleApi`.

  Two traps. It lives in **`foundation`**, so grepping `material3` for it finds nothing. And
  `styleable` is a *function*, so it compiles to `StyleModifierKt` — searching the jar's class
  names for "styleable" also finds nothing, and concluding the API is absent from that is wrong.
  Check with `javap -cp <foundation jar> androidx.compose.foundation.style.StyleModifierKt`, or
  list the `androidx/compose/foundation/style/` entries.

  The `styles` skill asks for androidx foundation 1.12.0-alpha01 or higher; that is androidx's
  numbering, and Compose Multiplatform's own 1.11.1 foundation already carries the API, so do not
  read that requirement as ruling CMP out.

- **`ColorScheme` has no `equals`.** It declares `copy` and `toString` but neither `equals` nor
  `hashCode`, so two schemes built from identical inputs compare unequal and any data class
  holding one inherits that. A spec asserting a palette is deterministic must compare it role by
  role; `shouldBe` on the whole thing fails with a several-kilobyte diff of identical values.
- **223 public entry points.** Presence in `components.md` is what settles whether a component
  exists in this version at all — check there before writing one, and before believing a web
  search that says M3 has it.

## Restyle through `*Defaults`, never by reimplementing

Every component takes a colours/elevation/shape parameter whose factory lives on its `*Defaults`
object (`ButtonDefaults.buttonColors(…)`, `CardDefaults.cardElevation(…)`). That is the supported
seam, and it is how `stx-material` resolves its `variant × color` matrix: one function returns
a `ButtonColors` built from `ButtonDefaults`, and the component itself is untouched. Copying a
component's source to change a colour is the failure mode this avoids.

## Where to read

| File | Holds |
| --- | --- |
| `references/color-scheme.md` | every `ColorScheme` role |
| `references/typography.md` | every `Typography` style |
| `references/shapes.md` | every `Shapes` slot |
| `references/defaults.md` | every `*Defaults` object |
| `references/components.md` | every public function in the package |

For how Material 3 behaves across platforms — and for anything about resources, lifecycle,
navigation or testing — read the `compose-multiplatform` skill instead.
