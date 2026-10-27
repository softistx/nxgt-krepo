---
"stx-material": minor
---

M3 Expressive for 0.3.0: a calmer palette, Material 3's shape morph on buttons, rows and groups, `style` on every interactive part, and a library whose layout reads in its imports. `0.x` minors carry breaking changes (`docs/consuming.md`, Pre-1.0), and this one has several.

## Breaking

- **Packages.** The code moved to `com.softistx.material.foundation.*` and `com.softistx.material.components.*`. The old to new table is in the `tidy-families-regroup` changeset; symbol names are unchanged.
- **`StxStyles` and `StxTheme.styles`** moved to `com.softistx.material.components` (they were in `foundation.style`). `StxStyles.field` and `StxStyles.listTile` are gone: they were empty (M3's text fields and `ListItem` dim and morph themselves), so pass `style = Style { … }` on its own.
- **The top-level `xStyle` vals are `internal`** (`buttonStyle`, `cardStyle`, `chipStyle`, `toggleStyle`, `uploadStyle`, `dimmedStyle`, `tableRowStyle`, `sortHeaderStyle`, `tileStyle`, `navigationItemStyle`, `breadcrumbStyle`, `stepperStyle`, `alertStyle`). Use `StxStyles.x` / `StxTheme.styles.x`. `DISABLED_ALPHA` stays public.
- **`StxTheme` and `StxThemeProvider` lose `motionScheme`.** `motion: StxMotion?` is the one motion input and Material 3's scheme derives from it: `enabled = false` installs a scheme whose every spec snaps, otherwise the motion's `scheme`. Replace `motionScheme = x` with `motion = StxMotion(x)`.
- **`style` moved last** (the last parameter, or the last before one trailing lambda) on `Button`, `ButtonSurface`, `BusyButton`, `ConfirmButton`, `ResponsiveButton` (they put `size` before it), `ActionChip` and `Chip` (`leading` before it), `ListTile` (`leading`, `selected`, `group` before it, `trailing` after), `Alert`, `AnnouncementBar` and `PromoBanner` (`icon` before it), `Search` (`trailing` before it), `SegmentedControl` (`icons` before it) and `DataTable` (`headerStyle` before it).
- **Positional breaks.** A call that passed these positionally must now name them: `Alert` and `AnnouncementBar` `action`, `DataTable` `onRowClick`, `Drawer` `content`, the `optionLabel` and `format` lambdas that follow `style`, and any argument listed in the previous item.
- **`SegmentedControl(icons)`** is `List<ImageVector> = emptyList()` instead of a nullable list.
- **`DataTable` `sortable`** is `TableColumn(header, sortable = true, value)` (so a positional `TableColumn("x", f)` must name `value`), not a `(Int) -> Boolean` on the table.
- **Button family.** The 0.97 press scale is gone from buttons, icon buttons and toggles (they morph their shape), `buttonStyle` keeps only the disabled alpha.

## Visible default changes

- A calmer palette: TonalSpot and `SPEC_2025`, one accent per screen, `Tone.Info` on `secondary` and a `Stat` dot only for Success, Warning and Error.
- `Emphasis.Medium` is 0.80 and `Emphasis.Subtle` 0.72 (were 0.72 and 0.5); `Subtle` now passes AA.
- `StxTheme` follows the OS "Reduce motion" setting when it is given no `motion`, and a reduced motion snaps Material 3's own morphs too.
- Buttons, icon buttons, toggles, split buttons, FABs, list tiles, segmented controls and view toggles morph their shape under the press; headers and titles are emphasised; notices carry their tone on a plate; flexible app bars; connected button groups.

## New API

- `size: ButtonSize` on the button family; `style: Style = Style` on every interactive part; `TableColumn.sortable` and sortable `DataTable` headers with `TableSort`.
- `StxMotion.system(reduceMotion)`, `systemReducesMotion()` and, on desktop, `readDesktopReducedMotion()`.
- `styledBy(…, selected: Boolean = false)` for the style state of a selectable part.
- `Typography(emphasized)`, `ListTile(selected, group)`, `Alert(icon)` with `AlertDefaults`, `NavigationSuiteDefaults.CompactBelow`, four more `StxIcons`.
