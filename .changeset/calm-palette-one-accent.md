---
"stx-material": minor
---

A calmer default palette, with one accent per screen.

- `stxColorScheme`, `stxColors(seed, …)`, `platformColorScheme` and `StxThemeProvider` take `paletteStyle` (default `PaletteStyle.TonalSpot`) and `specVersion` (default `ColorSpec.SpecVersion.SPEC_2025`). `Vibrant` and `Expressive` stay available. Android's wallpaper (dynamic colour) path is unchanged.
- Visible default change: `Tone.Info` now comes from the scheme's `secondary*` roles (`stxColors(info: Color? = null)`, where `null` means secondary) instead of a fixed blue, so there is no fourth hue near the primary. Pass a colour to `info` to keep a dedicated one.
- The `success` and `warning` roles grown from their own seeds are also pinned to `SPEC_2025`.
- Visible default change: the seed palette is TonalSpot on the 2025 spec, so seed-generated colours are more muted than before.
- Visible default change: `Stat` draws its delta as a line of text, with a coloured dot only for `Success`, `Warning` and `Error`; `Tone.Info` shows no dot.
