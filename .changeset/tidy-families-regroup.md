---
"stx-material": minor
---

The packages moved to `com.softistx.material.foundation.*` and `com.softistx.material.components.*`. The package move changes no behaviour; the other breaks of 0.3.0 are in the lead changeset. Fix the imports with the table below (the symbol names after the package are unchanged).

| Old package, under `com.softistx.material` | New package, under `com.softistx.material` |
| --- | --- |
| `theme` | `foundation.theme` |
| `theme.StyleTokens` (the `StyleScope.colors` / `scheme` / `shapes` / `spacing` / `motion` extensions) | `foundation.style` |
| `style` (`StxStyles`, `StxTheme.styles`) | `components` (`StxStyles` and `StxTheme.styles` live in `components.StxStyles`) |
| `motion` | `foundation.motion` |
| `layout`: `ResponsiveGrid`, `ScrollToTop`, `LoadMoreButton`, `SelectionBar`, `RefreshBox` | `components.layout` |
| `helpers` (`Breakpoints`, `wCompact`, `hExpanded`, …) | `foundation.layout` (window-size helpers only) |
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

`com.softistx.material.foundation.style` holds the `StyleScope` token extensions; `StxStyles` and `StxTheme.styles` are in `com.softistx.material.components`, so `foundation` knows no component.

## How to migrate

The Maven coordinates (`io.github.softistx:stx-material`) and the dependency line are unchanged; only imports change. The table is an old to new prefix map you can apply with search and replace, longest prefix first (`form.state` before `form`, `theme.StyleTokens` members before `theme`). A symbol keeps its name; only the package in front of it changes. Wildcard imports of the split packages have to be rewritten by hand:

- `display.*` becomes `components.content.*` plus `components.feedback.Alert`, `AnnouncementBar`, `PromoBanner`, `StatusBadge`, `StatusDot` and `Skeleton` (the types that went to `feedback`).
- `theme.*` becomes `foundation.theme.*`, plus `foundation.style.*` for the `StyleScope` extensions (`colors`, `scheme`, `shapes`, `spacing`, `motion`).
- `models.*` is split three ways: `UiState` and `ApiResponse` to `components.feedback`, `DataMode` to `components.form`, `QueryFilter` to `components.data`.
- `layout.*` becomes `components.layout.*`; `helpers.*` becomes `foundation.layout.*`.

