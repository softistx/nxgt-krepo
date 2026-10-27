---
"stx-material": minor
---

`Emphasis.Subtle` holds AA for body text, and `StxTheme` follows the OS reduce-motion and increase-contrast settings (#297). New API: `StxMotion.increaseContrast` (a third constructor parameter, default `false`), `StxMotion.system(scheme, accessibility)` and `systemAccessibility()` with its `SystemAccessibility(reduceMotion, increaseContrast)`. No signature of an existing parameter changes. Visible default changes:

- `Emphasis.Subtle` alpha 0.5 → 0.72, and `Emphasis.Medium` 0.72 → 0.86 so the three steps stay apart. Contrast of `Subtle` on `surface` / `surfaceContainer` / `surfaceContainerHigh`, measured off rendered pixels on the default theme: light 2.82 / 2.74 / 2.71 → 5.21 / 4.91 / 4.83, dark 4.49 / 4.38 / 4.27 → 8.21 / 7.81 / 7.40 (AA for body text is 4.5). `Medium`: light 4.83 to 5.21 → 7.03 to 7.90. Secondary text is darker in light and brighter in dark.
- `StxTheme` with no `motionScheme` and no `motion`: when the OS asks to reduce motion, every animation snaps (before: only Android's animator scale applied, iOS and desktop ignored it). Pass your own `motionScheme` or `motion` to keep the old behaviour. Desktop reads `defaults` (macOS), `gsettings` (GNOME) and `reg` (Windows high contrast) once per process; Windows reduce motion is not readable. `-Dstx.material.reduceMotion` and `-Dstx.material.increaseContrast` (`true` or `false`) win over the OS.
