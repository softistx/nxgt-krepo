---
"stx-material": minor
---

`Alert`, `AnnouncementBar` and `PromoBanner` lose their tone border and carry the tone on a leading plate; `EmptyState` finally fades in and can wear an icon plate. New parameters, all after the existing ones: `icon: ImageVector? = null` and `showIcon: Boolean = true` on the three notices (naming an `icon` with `showIcon = false` throws `IllegalArgumentException`), `icon: ImageVector? = null` on `EmptyState` (together with an `illustration` it throws). Visible default changes:

- Border: the 1 dp border in the tone colour is gone.
- Colours: the tone container (`Success`, `Info`, `Warning`) becomes `surfaceContainerHigh` with `onSurface` text; `Tone.Error` keeps its error container.
- Shape: `medium` (12 dp) becomes `largeIncreased` (20 dp).
- Icon: a 36 dp `MaterialShapes.Cookie6Sided` plate with the tone's icon (check, info, warning) now leads each notice, in the tone's container pair (the solid pair for an error); `showIcon = false` removes it. The gap between the plate and the text of an `AnnouncementBar` is `md` instead of `sm`.
- Title: an `Alert` title goes from `TitleSmall` to `TitleSmall` emphasised, and is announced as a heading.
- `EmptyState` animation: the fade-in now runs (it never did with `AnimatedVisibility(visible = true)`); its title goes from `TitleMedium` to `TitleLarge` emphasised and is announced as a heading; `icon` adds a 96 dp `SoftBurst` plate on `secondaryContainer`.
