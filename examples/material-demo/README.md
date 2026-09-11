# material-demo

The catalogue for `libs/stx-material`: every component, with typed controls beside it, and one
whole screen at the end.

```bash
./kotlin run -m md-desktop     # a window, sized for the three-pane layout
./kotlin run -m md-android     # with a device or emulator connected
```

## One application, three modules

A module has exactly one product type, and neither `android/app` nor `jvm/app` covers both
platforms — so the demo cannot be a single module. It is not three applications either:

| | |
| --- | --- |
| `md-catalog/` | `kmp/lib` on `[jvm, android]`. **Everything**: `MaterialDemo()`, the story registry, the panes, the knobs, the stories. |
| `md-desktop/` | `jvm/app`. A `Window` around `MaterialDemo()`. |
| `md-android/` | `android/app`. One `ComponentActivity` around `MaterialDemo()`, and a manifest. |

The launchers are about twenty lines each and contain no logic and no stories. If one starts
growing, something is in the wrong module.

There is no iOS launcher: the library declares the Apple targets, the demo runs where it can be run
from here. Adding `ios/app` on a macOS host touches nothing in `md-catalog`.

The three panes of the catalogue are Navigation 3 list-detail (`AdaptiveNavDisplay`): the story
list, then the stage with its knobs. Compact shows one at a time; a wide window shows both.

## Adding a story

```kotlin
val DisplayStories = storyGroup("Display") {
    story("Card") { knobs ->
        Card(variant = knobs.enumChoice("Variant", CardVariant.Filled)) { … }
    }
}
```

Register the group in `md-catalog/src/Catalog.kt`. **A component added in any phase registers its story
in the same change** — the catalogue is never caught up with afterwards.

A knob declares itself by being read: `knobs.flag("Enabled", true)` returns the current value and,
the first time it runs, tells the right-hand panel to draw a switch. There is no separate
declaration to keep in sync with the preview, which is the failure mode this design exists to
prevent. Four kinds: `flag`, `text`, `number`, `choice` (and `enumChoice` for an enum).

## What an application writes instead

The catalogue calls `StxTheme` directly because it drives all four of Material 3's inputs from
its header. An ordinary application does not:

```kotlin
StxThemeProvider(seed = Color(0xFF5B5BD6)) { App() }
```

That resolves the colour scheme through `platformColorScheme` — the wallpaper on Android 12+, the
seed everywhere else — and hands the rest to `StxTheme` untouched.

## What the catalogue is for

Two things beyond looking at components:

- **The theme dials in the header are the proof.** The seed, the dark switch and the motion scheme
  repaint and re-time every story, and no component is told about any of them. If something does
  not follow, it is holding a colour or a curve it should be reading.
- **The Motion control switches Material 3's own `MotionScheme`** between expressive, standard and
  off. Off stops this library's motion only — M3's built-in components keep animating, because a
  `MotionScheme` has no null. `motion/spatial-and-effects` shows the distinction M3 draws: the
  square overshoots its mark, the colour swatch does not.
- **Wallpaper colours appear as a switch only on Android 12+**, because `supportsDynamicColor` says
  so. A switch that cannot change anything is worse than no switch.
- **`screens/orders-screen` is the acceptance criterion of every phase.** A whole screen written
  with no plumbing — no `animate*AsState`, no transition, no interaction source. If it ever needs
  one, the phase is not done.

The catalogue's own chrome is deliberately built from the library (`ListTile` for the story list,
`Chip` for the seed picker and the choice knobs, `StatusBadge`, `Typography`), so anything awkward
to use shows up here first. `Switch`, `Slider` and `OutlinedTextField` come straight from Material 3
— stx-material has no form layer until phase 2, and the demo says so rather than faking one.

## The panes scroll, and on desktop they say so

Twenty-eight stories is taller than any window, so all three panes scroll. On desktop each one
carries a `PaneScrollbar` — a pane whose wheel works but whose edge is bare reads as a pane with
nothing below the fold, and whatever was added last is simply never found. It is `expect`/`actual`
rather than a flag: a finger already knows a list moves, so the Android side draws nothing.

## Tests

`test@jvm/` renders the catalogue headlessly with `ImageComposeScene` — skiko's native library comes
from `$compose.desktop.currentOs`, which is why they are jvm-only.

- `StoryListScrollTest` sends a real wheel event at the left pane and checks the pixels move.
- `EveryStoryRendersTest` renders **every** story in `CatalogGroups` and asserts each paints
  something, plus that the group list is the one the docs claim and that no two stories share an id.
  A story that throws at display time compiles perfectly and only shows itself when someone clicks
  that one entry; a group written but never registered is invisible in exactly the same way as a
  group that does not exist.

Run them with `./kotlin test -m md-catalog`.

---

Apache-2.0 · [Contributing](../../CONTRIBUTING.md) · [All the libraries](../../README.md)
