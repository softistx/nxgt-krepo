# stx-material

## 0.4.0

### Minor Changes

- [#369](https://github.com/softistx/nxgt-krepo/pull/369) [`0899dcd`](https://github.com/softistx/nxgt-krepo/commit/0899dcd02ae9dbf801ec89f96c0b5f1a3a889e4f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `ListDetailLayout(selected, modifier, wideThreshold, listWidth, placeholder, list, detail)` shows a list and its detail side by side when the width it is offered reaches `wideThreshold` (720 dp), and one at a time below it, with no Navigation 3 back stack, so it folds inside a destination or a narrow desktop window. The list pane is `listWidth` (380 dp) wide; selection is plain state. `ListDetailLayoutDefaults` holds the two numbers and `Placeholder(title, modifier)`, the default second pane (write `placeholder = { ListDetailLayoutDefaults.Placeholder("Select a student", it) }` for another text). Each pane keeps its `rememberSaveable` state across a fold (a list's scroll position also survives a list, detail, list round trip); the detail's state is dropped when `selected` turns false. It is not `ListDetail`, which stays the scene metadata of `AdaptiveNavDisplay`.

- [#368](https://github.com/softistx/nxgt-krepo/pull/368) [`7307e4c`](https://github.com/softistx/nxgt-krepo/commit/7307e4c009671a58eda06269618057be42da5982) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `StateHost(state, onRetry, modifier, errorTitle, retryLabel, loadingLabel, loading, content)` renders a `UiState<T>`: card-shaped skeletons that arrive in turn while it loads (announced as "Loading", a polite live region), your `content` on data (all three faces sit in a `Box(modifier)`), and an error `Alert` with a Retry button on failure; the strings are parameters. `SkeletonCards(modifier, count)` is the default loading face, public so a screen can compose it.

## 0.3.0

### Minor Changes

- [#349](https://github.com/softistx/nxgt-krepo/pull/349) [`7da0ea7`](https://github.com/softistx/nxgt-krepo/commit/7da0ea7bc24ae5df99516581faafebc8857bc27b) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Every interactive form part takes `style: Style = Style`, applied last. New API: `style` on `Autocomplete`, `SelectField`, `CopyField`, `Composer`, `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioGroup`, `CheckboxGroup`, `Checklist`, both `SliderField` overloads, `QuantityField`, `InlineEdit`, `TagField`, `OtpField`, `UploadField` and `ThemeToggle`; `StxStyles.toggle`, `StxStyles.upload` and `StxStyles.dimmed` (the 0.38 disabled fade of `OtpField` and `InlineEdit`); `StxStyles.field` had no effect and is gone (see the lead changeset); `Autocomplete(isError, supportingText)` like the other fields. Parameter placement: `style` comes after `enabled` (and after `isError` and `supportingText` on `Autocomplete`), so positional calls keep their meaning; it sits immediately before a trailing lambda (`optionLabel` on `Autocomplete`, `SelectField`, `RadioGroup`, `CheckboxGroup`; `format` on `SliderField`), so `RadioGroup(…) { it.label }` still compiles, and only a positional call that passed that named lambda must name it. Visible default changes:
  
  - Press: the rows of `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioGroup` and `CheckboxGroup` and the `UploadField` well scale to 0.99 under the pointer.
  - Disabled: `UploadField` and `OtpField` fade to the 0.38 disabled alpha, and a disabled `InlineEdit` fades its text (before: no fade).
  - Disabled `TextField` and `TextareaField`: the extra 0.38 fade is gone, M3's own dimming remains (before: dimmed twice).
  - `Autocomplete(isError, supportingText)` shows a complaint in the error colours; `isError` and `supportingText` come after `enabled`.

- [#301](https://github.com/softistx/nxgt-krepo/pull/301) [`fcfa009`](https://github.com/softistx/nxgt-krepo/commit/fcfa00986beca1c4d6ea9892f93142cc80f04905) Thanks [@SteveGT96](https://github.com/SteveGT96)! - A calmer default palette, with one accent per screen.
  
  - `stxColorScheme`, `stxColors(seed, …)`, `platformColorScheme` and `StxThemeProvider` take `paletteStyle` (default `PaletteStyle.TonalSpot`) and `specVersion` (default `ColorSpec.SpecVersion.SPEC_2025`). `Vibrant` and `Expressive` stay available. Android's wallpaper (dynamic colour) path is unchanged.
  - Visible default change: `Tone.Info` now comes from the scheme's `secondary*` roles (`stxColors(info: Color? = null)`, where `null` means secondary) instead of a fixed blue, so there is no fourth hue near the primary. Pass a colour to `info` to keep a dedicated one.
  - The `success` and `warning` roles grown from their own seeds are also pinned to `SPEC_2025`.
  - Visible default change: the seed palette is TonalSpot on the 2025 spec, so seed-generated colours are more muted than before.
  - Visible default change: `Stat` draws its delta as a line of text, with a coloured dot only for `Success`, `Warning` and `Error`; `Tone.Info` shows no dot.

- [#302](https://github.com/softistx/nxgt-krepo/pull/302) [`ddcbc6a`](https://github.com/softistx/nxgt-krepo/commit/ddcbc6a5b630d10fc2d346d099e3835926587eae) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Emphasised typography, one emphasised title per screen.
  
  - `Typography(…, emphasized = false)` and `TypographyVariant.style(emphasized = false)` map the 15 Material 3 roles to their `*Emphasized` style. It is a different axis from `emphasis`, which only dims the colour. M3's emphasised styles come back at the regular weight for most roles in 1.12.0-alpha03, so the weight is raised to `Medium` (`SemiBold` for roles already `Medium`) when the theme's own is not heavier.
  - `SectionHeader(…, emphasized = true)` and `EntityHeader(…, emphasized = true)`.
  - `Stat(…, reserveDelta = true)` keeps the height of a delta when there is none, so stats in a row share one height.
  - Visible default change: `SectionHeader` is `TitleMedium` emphasised (was `TitleSmall`), `EntityHeader` is `HeadlineMedium` emphasised (was `HeadlineSmall`), and `TypographyVariant.Metric` is display-sized (`displaySmall`, SemiBold) instead of `headlineMedium` SemiBold. A `Stat` without a delta is taller by one delta line.
  - The `Stat` figure caps its font scale at 1.3 so a display-sized number still fits half a phone row at large type.

- [#345](https://github.com/softistx/nxgt-krepo/pull/345) [`7e18924`](https://github.com/softistx/nxgt-krepo/commit/7e189247492324709111c6aaa1c20fd537e80962) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `SegmentedControl` and `ViewToggle` are Material 3's connected button group: toggle buttons that touch, round at the two ends, squarer at the seams and morphing under the press, the chosen one filled with no checkmark and still announced as a `RadioButton` (now also `selected`). `SegmentedControl` gains `icons: List<ImageVector>? = null`, `ViewToggle` gains `style: Style = Style`. Visible default changes: segments share one width (or span the width a caller fills), the old outlined `SegmentedButton` look is gone, and `ViewToggle` is one joined pair instead of two separate round icon toggles. Also fixed: `Tabs`' press give never ran, because the style's interaction source was not the one any tab used; each tab now has its own, so a pressed tab gives 0.97. `SegmentedControl` and `ViewToggle` morph instead and take no scale.

- [#351](https://github.com/softistx/nxgt-krepo/pull/351) [`58646c6`](https://github.com/softistx/nxgt-krepo/commit/58646c6f44f5f79b55d0b00108aaa5128d204451) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Every remaining interactive part takes `style: Style = Style`, applied last, and `SwipeActions` reaches screen readers and keyboards ([#288](https://github.com/softistx/nxgt-krepo/issues/288), 3/3). New API: `style` on `PinBar`, `MentionChip`, `FileChip`, `Rating`, `FilterBar`, `ReactionBar`, `LinkPreview`, `ExpandableText`, `CodeBlock`, `ReplyPreview`, `PersonCard`, `Gallery`, `LoadMoreButton`, `ScrollToTop`, `RefreshBox`, `HelpTip`, `Tooltip`, `HoverCard`, `ContextMenu`, `Drawer` and `SwipeActions`; `SwipeActions(customActions: List<CustomAccessibilityAction> = emptyList())`, whose labels are the caller's (set only when the list is not empty); `StxStyles.tile` (0.99 press scale of a clickable `Gallery` image). `PinBar`, `LinkPreview` and `PersonCard` reuse `StxStyles.card`, `MentionChip` and `FileChip` reuse `StxStyles.chip`. Parameter placement: `style` comes after the existing parameters and just before a trailing lambda (`LinkPreview(leading)`, `PersonCard(action)`, `Tooltip`/`HoverCard`/`ContextMenu`/`RefreshBox(content)`, `SwipeActions(content)`, `Drawer(content)`), so `Tooltip("x") { … }` and the like still compile. The one positional case that breaks: `Drawer(open, onDismiss, modifier, gesturesEnabled, drawer, content)` must now name `content` (`Drawer(open, onDismiss, modifier, gesturesEnabled, drawer) { … }` or `content = { … }`), since `style` sits between them; `SwipeActions` likewise puts `style` after `background`. Hosts with a single control forward `style` to it (`FilterBar`, `ReactionBar`, `Rating`, `ExpandableText`, `CodeBlock`, `ReplyPreview`, `LoadMoreButton`, `ScrollToTop`, `HelpTip`); hosts with several controls take none (`SelectionBar`, `Lightbox`, `FabMenu`, `OverflowBar`, `StepFooter`, `NavigationSuite`, `AppBar`). `Menu`, `Sheet` and `ConfirmDialog` take no `style`: M3 draws their popup in its own window and a probe showed `style` does not reach it. On the wrappers (`Tooltip`, `HoverCard`, `ContextMenu`, `Drawer`, `SwipeActions`, `RefreshBox`) there is no default look and the style state has no input behind it: only static properties and `disabled` apply; `pressed`, `hovered` and `focused` never fire. A caller's `style` is never dropped silently any more: without a click handler it still applies, and only the press look and the role go. Visible default changes:
  
  - `PinBar` with `onClick` presses to 0.99 (before: no scale); `LinkPreview` and `PersonCard` with `onClick` are unchanged.
  - `MentionChip` and `FileChip` (with `onClick`) press to 0.97 (before: no scale). A `FileChip` without `onClick` does not scale.
  - A clickable `Gallery` tile presses to 0.99 and is announced as a button (before: a bare click with no role and no press).
  - `DataTable`: the caller's `style` and `headerStyle` now also dress rows and headers that are not clickable (before: they were ignored without `onRowClick` or `onSortChange`).
  - `Card` with `onClick` is announced as a button (before: no role).
  - `SectionHeader`, the name of a `PricingCard` and the title of a `DangerZone` are announced as headings (before: plain text).
  - `SwipeActions` with `customActions` lists them in the row's actions menu (before: the swipe was the only way in).

- [#359](https://github.com/softistx/nxgt-krepo/pull/359) [`9049186`](https://github.com/softistx/nxgt-krepo/commit/90491864502c9f23f59578a0645bcd279777cbc8) Thanks [@SteveGT96](https://github.com/SteveGT96)! - M3 Expressive for 0.3.0: a calmer palette, Material 3's shape morph on buttons, rows and groups, `style` on every interactive part, and a library whose layout reads in its imports. `0.x` minors carry breaking changes (`docs/consuming.md`, Pre-1.0), and this one has several.
  
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

- [#354](https://github.com/softistx/nxgt-krepo/pull/354) [`ce30db2`](https://github.com/softistx/nxgt-krepo/commit/ce30db2b35589b863ef2b31ace912cccaecec466) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The compact `NavigationSuite` bar keeps every label on one line, `StxIcons` gains four icons, and `animateStagger` is driven by the frame clock ([#295](https://github.com/softistx/nxgt-krepo/issues/295)). New API: `NavigationSuiteDefaults.CompactBelow` (600 dp, the width under which the suite is a compact bar), `StxIcons.Bell`, `StxIcons.Printer`, `StxIcons.Phone` and `StxIcons.EditNote` (24 dp vectors in the style of the others). No signature changes. Visible default changes:
  
  - `NavigationSuite` on a compact window: a destination label is one line that ends in an ellipsis when it does not fit (before: a long label wrapped onto a second line). A compact bar holds three to five destinations (M3's rule); past that, labels truncate, so move the extra ones behind a "More" entry, a drawer or the rail. Medium and wide layouts are unchanged.
  - `Modifier.animateStagger`: the wait before an item arrives is counted in frames of the composition's clock (before: a wall-clock `delay`). The timing on screen is the same, within a frame; a headless `ImageComposeScene` render, which steps frames without waiting, now sees the item arrive instead of leaving it invisible. Reduced motion still shows the item at once.

- [#303](https://github.com/softistx/nxgt-krepo/pull/303) [`34c7532`](https://github.com/softistx/nxgt-krepo/commit/34c75327be026c35e5bc1b0e3ba2c5391b99f57e) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `IconButton`, `IconToggle`, `ToggleButton`, `SplitButton` and `Fab` morph their shape and no longer scale: icon buttons and toggles are round at rest and squarer pressed (toggles also while checked), through M3's `shapes =` overloads; the `SplitButton` trailing half is a toggle that is checked while its menu is open, so it changes shape and its chevron points up; `Fab` draws its own corner morph (the corners shrink to the theme's `small` slot while pressed) since M3's FAB has none. `SplitButton` and `Fab` gain `style: Style = Style`. One button-family style: `buttonStyle` (`StxStyles.button`) now keeps only the disabled alpha. Visible default changes: the 0.97 press scale is gone from icon buttons and toggle buttons, a checked toggle is squarer, and the `SplitButton` chevron flips while its menu is open. Sizes are unchanged. Also fixed, found in the look check: an Outlined `IconButton` or `IconToggle` draws its border in the button colour (it was near-black, invisible in dark), and an unchecked Tonal `IconToggle` takes the tone's content colour (its icon was black).

- [#347](https://github.com/softistx/nxgt-krepo/pull/347) [`e57e8d3`](https://github.com/softistx/nxgt-krepo/commit/e57e8d3798b6cc3143130f639bfbbb4adce95cbb) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `Alert`, `AnnouncementBar` and `PromoBanner` lose their tone border and carry the tone on a leading plate; `EmptyState` finally fades in. New API: `icon: ImageVector? = AlertDefaults.icon(tone)` on the three notices (`null` drops the plate, a vector replaces the icon), `EmptyStateDefaults.IconPlate(icon)` for `EmptyState`'s `illustration` slot, and `StxIcons.Touch`. Parameter placement: `icon` sits before `style`, and `style` before the trailing `action` lambda of `Alert` and `AnnouncementBar` (after `onDismiss` on `PromoBanner`, where `style` is last), so a trailing-lambda call `Alert("x") { … }` still compiles. Visible default changes:
  
  - Border: the 1 dp border in the tone colour is gone.
  - Colours: the tone container (`Success`, `Info`, `Warning`) becomes `surfaceContainerHigh` with `onSurface` text; `Tone.Error` keeps its error container.
  - Shape: `medium` (12 dp) becomes `largeIncreased` (20 dp).
  - Icon: a 36 dp `MaterialShapes.Cookie6Sided` plate with the tone's icon (check, info, warning) now leads each notice, in the tone's solid pair (`main` with `onMain`), inverted on an error (`onErrorContainer` plate, `errorContainer` icon) so it holds 3:1 on the error container; `icon = null` removes it. The gap between the plate and the text of an `AnnouncementBar` is `md` instead of `sm`.
  - Title: an `Alert` title goes from `TitleSmall` to `TitleSmall` emphasised, and is announced as a heading.
  - `EmptyState` animation: the fade-in now runs (it never did with `AnimatedVisibility(visible = true)`); its title goes from `TitleMedium` to `TitleLarge` emphasised and is announced as a heading; `EmptyStateDefaults.IconPlate(icon)` is a 96 dp `SoftBurst` plate on `secondaryContainer` for its `illustration` slot.

- [#350](https://github.com/softistx/nxgt-krepo/pull/350) [`91fa215`](https://github.com/softistx/nxgt-krepo/commit/91fa215b3395dff3a76d54fd7401d01f41a0837a) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Every interactive data and date part takes `style: Style = Style`, applied last, and `DataTable` gets sortable headers. New API: `style` on `DataTable` (plus `headerStyle` for the sortable headers), `Pagination`, `SortControl`, `CommandPalette`, `DateField`, `DateRangeField`, `TimeField` and `Calendar`; `DataTable(sort: TableSort? = null, onSortChange: ((TableSort) -> Unit)? = null)`, `TableColumn(header, sortable: Boolean = true, value)` and `TableSort(column, direction = Asc)`; `StxStyles.tableRow` (0.99 press scale of a clickable `DataTable` row) and `StxStyles.sortHeader` (0.97 press scale of a sortable header). Parameter placement: `style` comes after `enabled` (and after `label` on `Pagination`), and `DataTable` puts `sort`, `onSortChange`, `headerStyle`, `style` before `onRowClick`, so `DataTable(columns, rows) { row -> … }` still compiles; only a positional call that passed `onRowClick` as its fifth argument must name it. `CommandPalette` dresses its list of commands, not the dialog; `Calendar` and `CommandPalette` have no default style (M3's `DatePicker` and `AlertDialog` own their look). Visible default changes:
  
  - `DataTable`: with `onSortChange` every column header whose `TableColumn.sortable` is true (all by default) becomes a button that sorts (ascending, then flipped); the others stay plain text. The current sort shows an arrow and announces "Ascending" or "Descending", also with `sort` alone (before: plain text headers; sorting needed a `SortControl` beside it).
  - `DataTable`: with `onRowClick` the rows of the wide table are clickable and press to 0.99 (before: only the collapsed cards were clickable).
  - `EntityHeader`: the title is announced as a heading (before: plain text).

- [#355](https://github.com/softistx/nxgt-krepo/pull/355) [`0f9c461`](https://github.com/softistx/nxgt-krepo/commit/0f9c461abda455c53480635edf5ee9ad3482f35d) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `Emphasis.Subtle` holds AA for body text, and `StxTheme` follows the OS "Reduce motion" setting ([#297](https://github.com/softistx/nxgt-krepo/issues/297)). New API: `StxMotion.system(reduceMotion)`, `systemReducesMotion()` and, on desktop, `readDesktopReducedMotion()`. `StxTheme`'s `motion` becomes nullable with a `null` default (`motionScheme` is removed, see the lead changeset). Visible default changes:
  
  - `Emphasis.Subtle` alpha 0.5 → 0.72, and `Emphasis.Medium` 0.72 → 0.80 so the three steps stay apart. Contrast of `Subtle` on `surface` / `surfaceContainer` / `surfaceContainerHigh`, measured off rendered pixels on the default theme: light 2.82 / 2.74 / 2.71 → 5.21 / 4.91 / 4.83, dark 4.49 / 4.38 / 4.27 → 8.21 / 7.81 / 7.40 (AA for body text is 4.5). `Medium` (light 4.83 to 5.21 → 5.99 to 6.56, dark 7.40 to 8.21 → 8.76 to 9.87). Secondary text is darker in light and brighter in dark.
  - `StxTheme` given no `motion`: when the OS asks to reduce motion, every animation snaps, M3's included (before: only Android's animator scale applied; iOS and desktop ignored the setting). Pass a `motion` and the OS is not read. Android reads the animator scale, iOS `UIAccessibilityIsReduceMotionEnabled`; desktop follows only `-Dstx.material.reduceMotion=true` or `false` (absent: not reduced, no process is spawned), and an app opts in to the OS's answer with `readDesktopReducedMotion()` (`suspend`, `Dispatchers.IO`, `null` when unknown) passed in through `motion`.
  
  Reading the OS "Increase contrast" setting waits for its first consumer (a component that restyles for it); it is a follow-up on the roadmap.

- [#292](https://github.com/softistx/nxgt-krepo/pull/292) [`3a66f78`](https://github.com/softistx/nxgt-krepo/commit/3a66f786c0da776188277dfe964fe008da2bd972) Thanks [@SteveGT96](https://github.com/SteveGT96)! - The packages moved to `com.softistx.material.foundation.*` and `com.softistx.material.components.*`. The package move changes no behaviour; the other breaks of 0.3.0 are in the lead changeset. Fix the imports with the table below (the symbol names after the package are unchanged).
  
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

- [#346](https://github.com/softistx/nxgt-krepo/pull/346) [`aeefc43`](https://github.com/softistx/nxgt-krepo/commit/aeefc434cf1e04afbe71cf504eff36239a76c937) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `ListTile` is Material 3's interactive `ListItem`. New parameters, placed before `style` (see the lead changeset): `selected: Boolean? = null` makes a tile a single choice, as a list-detail's current row is (the chosen row takes M3's selected tone and is announced as a `RadioButton` that is or is not selected), and `group: ListTileGroup? = null` (`ListTileGroup(index, count)`) makes it one tile of a segmented list, spaced by the new `ListTileDefaults.GroupGap`. Both need `onClick` (M3 has no static segmented item): `selected` or `group` without one throws an `IllegalArgumentException`.
  
  Visible default changes of a clickable `ListTile`, before then after:
  - The press: the whole row scaled to 0.99 under a plain rectangular ripple; now M3's state layers (pressed, hovered, focused) and a shape that morphs, with no scale. `StxStyles.listTile` had no effect and is gone (see the lead changeset).
  - The rest shape: square corners; now M3's expressive 4 dp corners (`ListItemDefaults.shapes()`), 12 dp hovered, 16 dp pressed or focused, and the row is clipped to it.
  - Semantics: a `Role.Button` on the clickable modifier; now one merged node with `Role.Button` (M3's `ListItem` sets none), so a screen reader reads title, supporting text and trailing as a single button. That role holds only while `selected == null`; with `selected` it is a `RadioButton`.
  - A grouped tile sits on `surfaceContainerLow` instead of the plain `surface` an ungrouped tile has.
  A tile without `onClick` is unchanged.

- [#348](https://github.com/softistx/nxgt-krepo/pull/348) [`031055b`](https://github.com/softistx/nxgt-krepo/commit/031055b82f15e9b80b5cd54f788fa6dea1d1b969) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Flexible app bars, a morphing Stepper marker and a contained `LoadingMark`.
  
  - `AppBar(…, scrollBehavior: TopAppBarScrollBehavior? = null)`, placed before the trailing `actions` lambda so existing calls compile. Given `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`, a `Medium` or `Large` bar collapses into the small bar.
  - `LoadingMark(…, contained = false)`: `true` seats the mark on a `primaryContainer` disc (M3's `ContainedLoadingIndicator`), determinate or not.
  - Visible default changes: `AppBarSize.Medium` and `Large` are M3's flexible bars (`MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`), so their expanded title is `headlineMedium` / `displaySmall` where it was `titleLarge`, and it wraps instead of staying on one line. The expanded height is 112 dp before and after for `Medium`, and 152 dp before, 120 dp after for `Large`. The title of every bar is emphasised and announced as a heading: `Small` and `Centered` use `titleLarge` emphasised, on one line (was `titleLarge`, plain, unbounded). The `Stepper` marker is a 32 dp plate (was a 28 dp rounded square): a circle, or a `Cookie9Sided` for the current step, morphing on `motion.spatial` when the step advances.

- [#352](https://github.com/softistx/nxgt-krepo/pull/352) [`0c8f7e9`](https://github.com/softistx/nxgt-krepo/commit/0c8f7e99b1b7dc85aee64280943c3047d6f8271b) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `Carousel` is rebuilt on M3's `HorizontalMultiBrowseCarousel` and takes `style: Style = Style` ([#289](https://github.com/softistx/nxgt-krepo/issues/289)). Every existing parameter keeps its meaning: `count`, `modifier`, `peek` (the width of the page peeking at the end, 48 dp by default) and the trailing `page` lambda. New API: `style`, placed just before `page`, so `Carousel(3) { … }` still compiles. `style` dresses the carousel as a whole (the track and its dots), never the pages, and has no default look; it is a layout wrapper, so only static properties and `disabled` apply. Visible default changes:
  
  - The large page is `width - peek - spacing.sm` wide and the peeking page is `peek` wide (before: every page was `width - peek` wide, with the next one's edge showing past it). Pages between shrink into the peeking one as you swipe.
  - Each page is cut to M3's `extraLarge` rounded mask that morphs with the page's size (before: pages were not clipped by the carousel).
  - Swiping flings with M3's multi-browse snapping (before: Foundation's pager snapping).
  - Narrow containers: the large page shrinks to leave room for the peeking one, whatever the width (M3 fits one large and one `peek`-wide page into the viewport).
  - Screen readers: each page announces its position in the collection (before: none) and the dots are hidden from them (before: unlabelled shapes).
  - The dots follow the carousel's current item (before: the pager's current page).

- [#300](https://github.com/softistx/nxgt-krepo/pull/300) [`f9aaf20`](https://github.com/softistx/nxgt-krepo/commit/f9aaf2035713477b586d1c96ecfa2c645deae4f8) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `Button` and `ButtonSurface` morph their shape on press (a pill at rest, squarer while pressed, through M3's `shapes =` overload) and no longer scale. New `size: ButtonSize` (`ExtraSmall` to `ExtraLarge`, default `Small`, the former 40 dp), also on `BusyButton`, `ConfirmButton` and `ResponsiveButton`. Visible default change: the press gives with its corners instead of a 0.97 scale. A Link button keeps its 40 dp height at every size instead of growing with `size`. `ResponsiveButton`'s collapsed icon button takes no `size`, and its expanded label now ellipsises.

## 0.2.3

### Patch Changes

- [#250](https://github.com/softistx/nxgt-krepo/pull/250) [`a532490`](https://github.com/softistx/nxgt-krepo/commit/a532490c89e008572840e11ec4e1350b69056fd6) Thanks [@SteveGT96](https://github.com/SteveGT96)! - Moves the Compose dependencies stx-material exports to their stable lines: Navigation3 1.2.0
  (from 1.2.0-alpha02), Material 3 Adaptive 1.3.0 (from 1.3.0-beta02), and Coil 3.6.3.

- [#262](https://github.com/softistx/nxgt-krepo/pull/262) [`e7a0481`](https://github.com/softistx/nxgt-krepo/commit/e7a0481579ce8626699bd8091dda570c4741fc9f) Thanks [@SteveGT96](https://github.com/SteveGT96)! - `Checkbox`, `Switch` and `RadioGroup` rows keep their 48 dp touch target and `Checkbox` applies the caller's modifier to its outermost node, and `ListTile`, `Breadcrumb`, `Stepper`, `Accordion` and `Disclosure` are announced as buttons and show Material's focus and press indication again.

## 0.2.2

### Patch Changes

- [#233](https://github.com/softistx/nxgt-krepo/pull/233) [`e4325bf`](https://github.com/softistx/nxgt-krepo/commit/e4325bfb4a3521a87a3129c88019913e83534c46) Thanks [@SteveGT96](https://github.com/SteveGT96)! - No change to the library itself. This is the first end-to-end run of the per-family release
  circuit, cut deliberately on the family with **no dependents** so that it moves one version line
  and publishes one batch. What it carries is the corrected note in the manifest and the README about
  which command compiles the Apple targets: `./kotlin publish` cross-compiles both on Linux,
  `./kotlin build` skips them.

<!-- Everything below the title is written by Changesets, newest first; this note is a footer
     because anything between the title and the first section would be pushed down by every
     release. -->

---

This is the release line for `io.github.softistx:stx-material`.

Everything through **0.2.1** was released in lockstep with every other library in this repository,
under one shared version and one shared changelog. That history is in
[the root `CHANGELOG.md`](../../../CHANGELOG.md); this file starts where it stops.
