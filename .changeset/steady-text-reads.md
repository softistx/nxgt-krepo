---
"stx-material": minor
---

`Emphasis.Subtle` holds AA for body text, and `StxTheme` follows the OS "Reduce motion" setting (#297). New API: `StxMotion.system(reduceMotion)`, `systemReducesMotion()` and, on desktop, `readDesktopReducedMotion()`. `StxTheme`'s `motion` becomes nullable with a `null` default (`motionScheme` is removed, see the lead changeset). Visible default changes:

- `Emphasis.Subtle` alpha 0.5 → 0.72, and `Emphasis.Medium` 0.72 → 0.80 so the three steps stay apart. Contrast of `Subtle` on `surface` / `surfaceContainer` / `surfaceContainerHigh`, measured off rendered pixels on the default theme: light 2.82 / 2.74 / 2.71 → 5.21 / 4.91 / 4.83, dark 4.49 / 4.38 / 4.27 → 8.21 / 7.81 / 7.40 (AA for body text is 4.5). `Medium` (light 4.83 to 5.21 → 5.99 to 6.56, dark 7.40 to 8.21 → 8.76 to 9.87). Secondary text is darker in light and brighter in dark.
- `StxTheme` given no `motion`: when the OS asks to reduce motion, every animation snaps, M3's included (before: only Android's animator scale applied; iOS and desktop ignored the setting). Pass a `motion` and the OS is not read. Android reads the animator scale, iOS `UIAccessibilityIsReduceMotionEnabled`; desktop follows only `-Dstx.material.reduceMotion=true` or `false` (absent: not reduced, no process is spawned), and an app opts in to the OS's answer with `readDesktopReducedMotion()` (`suspend`, `Dispatchers.IO`, `null` when unknown) passed in through `motion`.

Reading the OS "Increase contrast" setting waits for its first consumer (a component that restyles for it); it is a follow-up on the roadmap.
