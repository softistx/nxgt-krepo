# material-demo

The catalogue for `libs/ui/stx-material`: every component, with typed controls beside it, and one
whole screen at the end.

```bash
./kotlin run -m md-desktop              # the catalogue: a window, sized for the three-pane layout
./kotlin run -m md-desktop -- school    # the School showcase app, in the same window
./kotlin run -m md-android              # with a device or emulator connected; two launcher icons
```

## One application, four modules

A module has exactly one product type, and neither `android/app` nor `jvm/app` covers both
platforms — so the demo cannot be a single module. It is not three applications either:

| | |
| --- | --- |
| `md-catalog/` | `kmp/lib` on `[jvm, android]`. **Everything**: `MaterialDemo()`, the story registry, the panes, the knobs, the stories. |
| `md-school/` | `kmp/lib` on `[jvm, android]`. **The School showcase app**: `SchoolApp()`, six screens, in-memory data. See below. |
| `md-desktop/` | `jvm/app`. A `Window` around `MaterialDemo()`, or around `SchoolApp()` when started with `-- school`. |
| `md-android/` | `android/app`. One `ComponentActivity` per app (`MaterialDemo()` and `SchoolApp()`), and a manifest with both. |

The launchers are short and contain no logic and no stories. The one
decision `md-desktop` makes is which app the window holds, and it takes it from the first program
argument (`school`) rather than from a second `main`, a flag in the catalogue or a third module;
`md-android` makes it by declaring a second launcher activity, so both apps install together and are
one tap apart. If one starts
growing, something is in the wrong module.

There is no iOS launcher: the library declares the Apple targets, the demo runs where it can be run
from here. Adding `ios/app` on a macOS host touches nothing in `md-catalog`.

The three panes of the catalogue are Navigation 3 list-detail (`AdaptiveNavDisplay`): the story
list, then the stage with its knobs. Compact shows one at a time; a wide window shows both.

## The School app

`md-school` is a realistic school-management app written **only** against `stx-material` — what the
single-component stories cannot show: a navigation suite that becomes a bar, a rail or both as the
window grows, forms with validation, tables, list-detail, and every screen's loading, empty and
error face. It is also the evaluation bench for the visual work on the library, so it uses the
components in their current look and restyles nothing.

```bash
./kotlin run -m md-desktop -- school
./kotlin test -m md-school -p jvm
```

| Screen | What it holds |
| --- | --- |
| Home (`Dashboard`) | stats, today's timetable, recent grades, announcements, a to-do list, pull to refresh |
| Students | searchable, filterable, sortable list or grid; detail with profile, grades, attendance, contacts; an enrolment wizard |
| Grades | filtered, sortable table; a validated form to enter or correct a mark |
| Timetable | a week of lesson cards on a wide window, a tab per day on a phone; calendar |
| Messages | conversations (swipe to archive), thread with bubbles, quick replies and a composer |
| Settings | light / dark / system, accent colour, language, notification switches, security, help, and the showcase's own switches |

Layout is `src/<screen>/` per screen, `src/common/` for what two screens share (the screen frame,
the loading/error host, the list-detail split), `src/session/` for the state they read, and
`src/data/` for the data: about thirty students, their marks, a timetable, conversations and
announcements, all built from index arithmetic with no randomness and a fixed "now"
(`SchoolClock`), so a screenshot is the same tomorrow.

**Testing a screen without clicking.** `SchoolScreen.entries` lists the six screens and
`SchoolApp(initialScreen, start)` opens any of them on any face: `SchoolStart` carries the colour
mode, the `DataState` (`Loaded`, `Loading`, `Empty`, `Failed`), an open student or conversation, and
`animated = false` so the motion helpers settle at once. `test@jvm/SchoolRender.kt` turns that into
`renderSchool(screen, viewport, dark, start)`, and `EveryScreenRendersTest` walks the grid: six
screens at phone (412 x 915) and desktop (1440 x 900), light and dark, then each screen on the three
non-default data states, then the two detail views. Set `SCHOOL_SHOTS_DIR=/some/dir` and every render
is also written there as a PNG.

The `DataState` is also a control in Settings ("Showcase"), so the same states can be looked at by
hand.

## Adding a story

```kotlin
val ContentStories = storyGroup("Content") {
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
