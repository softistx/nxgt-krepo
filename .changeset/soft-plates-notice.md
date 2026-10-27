---
"stx-material": minor
---

`Alert`, `AnnouncementBar` and `PromoBanner` lose their tone border and carry the tone on a leading plate; `EmptyState` finally fades in. New API: `icon: ImageVector? = AlertDefaults.icon(tone)` on the three notices (`null` drops the plate, a vector replaces the icon), `EmptyStateDefaults.IconPlate(icon)` for `EmptyState`'s `illustration` slot, and `StxIcons.Touch`. Parameter placement: `icon` sits before `style`, and `style` before the trailing `action` lambda of `Alert` and `AnnouncementBar` (after `onDismiss` on `PromoBanner`, where `style` is last), so a trailing-lambda call `Alert("x") { … }` still compiles. Visible default changes:

- Border: the 1 dp border in the tone colour is gone.
- Colours: the tone container (`Success`, `Info`, `Warning`) becomes `surfaceContainerHigh` with `onSurface` text; `Tone.Error` keeps its error container.
- Shape: `medium` (12 dp) becomes `largeIncreased` (20 dp).
- Icon: a 36 dp `MaterialShapes.Cookie6Sided` plate with the tone's icon (check, info, warning) now leads each notice, in the tone's solid pair (`main` with `onMain`), inverted on an error (`onErrorContainer` plate, `errorContainer` icon) so it holds 3:1 on the error container; `icon = null` removes it. The gap between the plate and the text of an `AnnouncementBar` is `md` instead of `sm`.
- Title: an `Alert` title goes from `TitleSmall` to `TitleSmall` emphasised, and is announced as a heading.
- `EmptyState` animation: the fade-in now runs (it never did with `AnimatedVisibility(visible = true)`); its title goes from `TitleMedium` to `TitleLarge` emphasised and is announced as a heading; `EmptyStateDefaults.IconPlate(icon)` is a 96 dp `SoftBurst` plate on `secondaryContainer` for its `illustration` slot.
