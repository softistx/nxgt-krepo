---
"stx-material": minor
---

Emphasised typography, one emphasised title per screen.

- `Typography(…, emphasized = false)` and `TypographyVariant.style(emphasized = false)` map the 15 Material 3 roles to their `*Emphasized` style. It is a different axis from `emphasis`, which only dims the colour. M3's emphasised styles come back at the regular weight for most roles in 1.12.0-alpha03, so the weight is raised to `Medium` (`SemiBold` for roles already `Medium`) when the theme's own is not heavier.
- `SectionHeader(…, emphasized = true)` and `EntityHeader(…, emphasized = true)`.
- `Stat(…, reserveDelta = true)` keeps the height of a delta when there is none, so stats in a row share one height.
- Visible default change: `SectionHeader` is `TitleMedium` emphasised (was `TitleSmall`), `EntityHeader` is `HeadlineMedium` emphasised (was `HeadlineSmall`), and `TypographyVariant.Metric` is display-sized (`displaySmall`, SemiBold) instead of `headlineMedium` SemiBold. A `Stat` without a delta is taller by one delta line.
- The `Stat` figure caps its font scale at 1.3 so a display-sized number still fits half a phone row at large type.
