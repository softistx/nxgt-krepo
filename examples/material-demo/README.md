# material-demo

The catalogue for `libs/shared-material`: every component, with typed controls beside it, and one
whole screen at the end.

```bash
./kotlin run -m desktop     # a window, sized for the three-pane layout
./kotlin run -m android     # with a device or emulator connected
```

## One application, three modules

A module has exactly one product type, and neither `android/app` nor `jvm/app` covers both
platforms — so the demo cannot be a single module. It is not three applications either:

| | |
| --- | --- |
| `catalog/` | `kmp/lib` on `[jvm, android]`. **Everything**: `MaterialDemo()`, the story registry, the panes, the knobs, the stories. |
| `desktop/` | `jvm/app`. A `Window` around `MaterialDemo()`. |
| `android/` | `android/app`. One `ComponentActivity` around `MaterialDemo()`, and a manifest. |

The launchers are about twenty lines each and contain no logic and no stories. If one starts
growing, something is in the wrong module.

There is no iOS launcher: the library declares the Apple targets, the demo runs where it can be run
from here. Adding `ios/app` on a macOS host touches nothing in `catalog`.

## Adding a story

```kotlin
val DisplayStories = storyGroup("Display") {
    story("Card") { knobs ->
        Card(variant = knobs.enumChoice("Variant", CardVariant.Filled)) { … }
    }
}
```

Register the group in `catalog/src/Catalog.kt`. **A component added in any phase registers its story
in the same change** — the catalogue is never caught up with afterwards.

A knob declares itself by being read: `knobs.flag("Enabled", true)` returns the current value and,
the first time it runs, tells the right-hand panel to draw a switch. There is no separate
declaration to keep in sync with the preview, which is the failure mode this design exists to
prevent. Four kinds: `flag`, `text`, `number`, `choice` (and `enumChoice` for an enum).

## What the catalogue is for

Two things beyond looking at components:

- **The theme dials in the header are the proof.** One seed and one dark switch repaint every story,
  and no component is told about either. If something does not follow, it is holding a colour it
  should be reading.
- **`screens/orders-screen` is the acceptance criterion of every phase.** A whole screen written
  with no plumbing — no `animate*AsState`, no transition, no interaction source. If it ever needs
  one, the phase is not done.

The catalogue's own chrome is deliberately built from the library (`ListTile` for the story list,
`Chip` for the seed picker and the choice knobs, `StatusBadge`, `Typography`), so anything awkward
to use shows up here first. `Switch`, `Slider` and `OutlinedTextField` come straight from Material 3
— shared-material has no form layer until phase 2, and the demo says so rather than faking one.
