---
"stx-material": minor
---

BREAKING: the packages moved to `com.softistx.material.foundation.*` and `com.softistx.material.components.*`. Nothing else changed: no signature, no behaviour. Fix the imports with the table below (the symbol names after the package are unchanged).

| Old package, under `com.softistx.material` | New package, under `com.softistx.material` |
| --- | --- |
| `theme` | `foundation.theme` |
| `theme.StyleTokens` (the `StyleScope.colors` / `scheme` / `shapes` / `spacing` / `motion` extensions) | `foundation.style` |
| `style` (`StxStyles`, `StxTheme.styles`) | `foundation.style` |
| `motion` | `foundation.motion` |
| `layout` | `foundation.layout` |
| `helpers` (`wCompact`, `hExpanded`, …) | `foundation.layout` |
| `icon` | `foundation.icon` |
| `text` | `foundation.text` |
| `button` | `components.button` |
| `form` | `components.form` |
| `form.state` | `components.form.state` |
| `navigation` | `components.navigation` |
| `feedback` | `components.feedback` |
| `display`: `Alert`, `AnnouncementBar`, `PromoBanner`, `StatusBadge`, `StatusDot`, `Skeleton` | `components.feedback` |
| `display`: everything else (`Card`, `ListTile`, `Stat`, `EmptyState`, the chips, `MessageBubble`, `Kbd`, `PricingCard`, …) | `components.content` |
| `surface` | `components.surface` |
| `media` | `components.media` |
| `data` | `components.data` |
| `datetime` | `components.datetime` |
| `models.UiState`, `ApiResponse` | `components.feedback` |
| `models.DataMode` | `components.form` |
| `models.QueryFilter` | `components.data` |

`com.softistx.material.foundation.style` now holds both `StxStyles` and the `StyleScope` token extensions, so a file that imported `theme.colors` and `style.StxStyles` imports both from one package.
