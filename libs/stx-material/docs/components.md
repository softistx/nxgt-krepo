# Components

Every component the library ships, what it takes, and where to see it. **This is the file a new
component is documented in**, in the change that adds it.

Open the catalogue with `./kotlin run -m md-desktop`; the story id is the line under each story's
title. Every component takes `modifier: Modifier`, and every *interactive* one takes
`style: Style = Style` — both are left out of the tables below because they are on everything that
has them.

**Most of these are Material 3's components, dressed.** `Button` is M3's `Button`, `Chip` is its
`FilterChip`, `Card`, `ListTile` and `StatusBadge` are its `Card`, `ListItem` and `Badge`. What is
added is the default that was missing: the colour matrix resolved into M3's own `*Colors`, the
padding and rhythm inside a card, a hover state M3's chip does not have, and a press that gives
under the finger. Only `Alert`, `EmptyState`, `Skeleton`, `ResponsiveButton`, `Rating`, `Stat`,
`LabeledDivider`, `FilterBar` and `UploadField` are built from primitives, because M3 has nothing
to start from. AGENTS.md's *Building a component* is the rule.

## Foundation

| Story | Shows |
| --- | --- |
| `foundation/typography` | The 20 `TypographyVariant` roles at each `Emphasis` |
| `foundation/spacing` | The eight-step spacing scale, to scale |
| `foundation/shapes` | Material 3's eight shape slots, expressive ones included |
| `foundation/icons` | The library's icon set |
| `foundation/semantic-colours` | `success` / `info` / `warning` / `error`, main and container |

## Motion

| Story | Shows |
| --- | --- |
| `motion/spatial-and-effects` | The two axes side by side — the square overshoots, the swatch does not |
| `motion/every-speed-at-once` | `Fast` / `Default` / `Slow` on the same move |

The header's **Motion** control switches the whole tree between `MotionScheme.expressive()`,
`MotionScheme.standard()` and off. Off stops this library's motion; Material 3's own components
keep their built-in animations, because a `MotionScheme` has no null.

## Text

| Component | Parameters | Story |
| --- | --- | --- |
| `Typography` | `text`, `variant = BodyMedium`, `emphasis = Full`, `color`, `align`, `maxLines`, `overflow` | `foundation/typography` |

`TypographyVariant` is the 15 Material 3 roles plus five this library adds: `Overline`, `Caption`,
`Metric` (a figure meant to be read at a glance), `Code` (monospaced), `Link` (underlined).

`Emphasis` — `Full` (1.0), `Medium` (0.72), `Subtle` (0.5) — is an alpha on the content colour, so
secondary text stays in the palette instead of picking a grey.

## Icons

| Component | Parameters | Story |
| --- | --- | --- |
| `Icon` | `icon: ImageVector`, `description: String?`, `size = Medium`, `tint` | `foundation/icons` |

`description` has **no default**. A caller must decide whether the icon carries meaning (a string)
or repeats the label beside it (`null`); it is the one accessibility decision that cannot be made
correctly on the caller's behalf.

`IconSize` — `Small` 16, `Medium` 20, `Large` 24, `XLarge` 32 dp.

`StrangeIcons` holds twenty-one hand-built vectors: `Add`, `Check`, `Close`, `ChevronLeft`,
`ChevronRight`, `ChevronDown`, `ChevronUp`, `Eye`, `EyeOff`, `Delete`, `Edit`, `Inbox`, `Person`,
`Home`, `Menu`, `MoreHoriz`, `Calendar`, `Schedule`, `Star`, `Search`, `Warning`. They are defined
in code because **no icon pack is reachable from here**: the Kotlin Toolchain's `$compose` catalog
has no key for the Material icons, `$compose.material` does not carry `material-icons-core` in
Compose Multiplatform 1.11, and the AndroidX icon artifacts are Android-only. An application that
wants a thousand glyphs should depend on a pack directly and pass the `ImageVector` in — every
component here takes one.

## Buttons

| Component | Parameters | Story |
| --- | --- | --- |
| `Button` | `text`, `onClick`, `variant = Filled`, `color = Primary`, `enabled` | `buttons/button` |
| `ButtonSurface` | as `Button`, plus a `RowScope` `content` slot instead of `text` | `buttons/button` |
| `IconButton` | `icon`, `description`, `onClick`, `variant = Ghost`, `color = Neutral`, `size`, `enabled` | `buttons/icon-button` |
| `ResponsiveButton` | `text`, `icon`, `onClick`, `variant`, `color`, `enabled`, `collapseBelow = 360.dp` | `buttons/responsive-button` |
| `ButtonRow` | `align = End`, `content: RowScope` | `buttons/button-row` |
| `Fab` | `icon`, `description`, `onClick`, `text?`, `expanded = true`, `color = Primary` | `buttons/fab` |
| `FabMenu` | `expanded`, `onExpandedChange`, `actions: List<FabAction>` | `buttons/fab-menu` |
| `SplitButton` | `text`, `onClick`, `overflow: List<MenuItem>`, `enabled` | `buttons/split-button` |

`ButtonVariant` — `Filled`, `Tonal`, `Outlined`, `Ghost`, `Link`.
`ButtonColor` — `Primary`, `Secondary`, `Success`, `Info`, `Warning`, `Danger`, `Neutral`.

The 5 × 7 matrix is resolved in **one** function, `buttonColors(variant, color)`, which returns
Material 3's own `ButtonColors` — so M3 paints the container, the ripple and the disabled treatment,
and this library only decides *which* colours. Adding a colour touches one enum entry and one `when`
branch; see `buttons/variant-and-colour-matrix` for the whole grid at once. `buttonBorder` answers
the same way for the outline, and `buttonStyle` is what is left over: the press scale, which M3 has
no parameter for.

`ButtonRow` is a layout — it lines buttons up and spaces them. It is deliberately not named
`ButtonGroup`: Material 3 has a `ButtonGroup`, and that one is a connected segmented control taking
a `ButtonGroupScope`. Two different things should not share a name.

`ResponsiveButton` measures the width it is *offered*, not the window, so it folds inside a narrow
pane on a wide screen too. Its label becomes the icon's `contentDescription` when it collapses, so
the button never goes silent.

`Fab` is M3's `FloatingActionButton`, using the *container* pair of `ButtonColor` — M3's own FAB
default. Pass `text` and it becomes the extended FAB. `FabMenu` is M3's `FloatingActionButtonMenu`:
the main button swaps Add for Close, and each `FabAction` is a menu item that closes the fan on
click. `SplitButton` is M3's `SplitButtonLayout`: the leading half is the primary action, the
trailing chevron opens a `Menu` of alternatives.

**Collapsed, it is an `IconButton`** — round, 40 × 40, M3's own metrics — not a pill with the label
taken out. The two forms are two components and `AnimatedContent` morphs between them.
`ResponsiveButtonTest` renders it and measures the box: 40 × 40 collapsed, 130 × 40 expanded.

## Display

| Component | Parameters | Story |
| --- | --- | --- |
| `Card` | `variant = Filled`, `onClick: (() -> Unit)? = null`, `enabled`, `content: ColumnScope` | `display/card` |
| `Chip` | `text`, `selected`, `onClick?`, `enabled`, `leading?`, `trailing?` | `display/chip` |
| `StatusBadge` | `text`, `tone = Info` — no `style`: a badge is inert | `display/status-badge` |
| `ListTile` | `title`, `supporting?`, `onClick?`, `enabled`, `leading?`, `trailing?` | `display/list-tile` |
| `Alert` | `text`, `tone = Info`, `title?`, `visible = true`, `action?` | `display/alert` |
| `EmptyState` | `title`, `description?`, `illustration?`, `action?` | `display/empty-state` |
| `Skeleton` | `height = 16.dp`, `shape = shapes.extraSmall` | `display/skeleton` |
| `Stat` | `value`, `label`, `delta?`, `tone = Info` | `display/stat` |
| `FilterBar` | `options`, `selected`, `onChange` | `display/filter-bar` |
| `Rating` | `value`, `onChange`, `max = 5`, `enabled` | `display/rating` |
| `LabeledDivider` | `label` — no `style`: a divider is inert | `display/labeled-divider` |

`CardVariant` — `Filled`, `Outlined`, `Elevated`.

A `Card` is interactive **iff** `onClick` is not null: it grows a hover and a press state only when
it does something. `ListTile` follows the same rule.

`Chip` is M3's `FilterChip`, so selection, the tick mark, the border and the shape morph are all
M3's and animate on their own. The hover is not: `SelectableChipColors` has thirteen colours and no
hover among them — M3's own source carries the `TODO(…): Support other states: hover, focus, drag` —
so `chipColors(hovered)` adds the state layer M3 would have, composited over whichever container the
chip is already wearing and faded on the effects axis. A chip with no `onClick` does not light up,
because it does nothing.

The tint is **8 % over a transparent container and 16 % over a filled one**, which looks like a
fudge and is a measurement. A selected chip in a real screen is the one that was just clicked, so it
already wears Material's focus layer; a second 8 % on top of that shifts the pixel by 0.068 while
the same 8 % over a transparent container shifts it by 0.145 — visible in one place and not the
other, which is exactly how it was reported. `ChipHoverTest` renders both cases and holds the
floor.

`Alert` owns its own enter and exit — pass `visible` and it animates itself; there is no
`AnimatedVisibility` for the caller to write. `EmptyState` fades in for the same reason, and
`Skeleton` shimmers on its own.

`Stat` is a `Card` wearing `TypographyVariant.Metric`. `FilterBar` is a row of `Chip`s — not the
filter subsystem of phase 7, a selected subset of names. The empty set is "everything". `Rating`
is a row of `IconButton`s; M3 has no rating control. `LabeledDivider` is M3's `HorizontalDivider`
with a word in the gap.

## Forms

The part of the library that removes the most work, because a form is where plumbing usually lives.
A control takes its `FieldState` and nothing else is wired: the value, the change handler, the
error, and *when the error is allowed to appear* all come from it.

```kotlin
val form = rememberForm()
val email = form.field("email", "", Rules.required(), Rules.email())
val terms = form.field("terms", false, Rules.checked())

TextField(email, label = "Email")
Checkbox(terms, label = "I accept the terms")
Button("Create account", onClick = { form.submit { register(form.values()) } })
```

| Type | What it holds | Where it lives |
| --- | --- | --- |
| `FieldState<T>` | one input: value, validity, `touched`, `dirty`, `showError` | `rememberField(initial, vararg rules)` |
| `FormState` | the fields that submit together: `isValid`, `dirty`, `validate()`, `submit { }`, `reset()`, `values()` | `rememberForm()` + `form.field(name, initial, vararg rules)` |
| `Validation<T>` | a rule, as `(T) -> String?` — `null` passes, anything else is the message | `Rules.*`, combined with `and` |

**An error is held until it is earned.** A required field is invalid the moment an empty form is
drawn, and showing that immediately greets someone with six complaints about work they have not
started. So a field speaks once it has been *left after being typed in*, or once `form.validate()`
demands it. `showError` is the only thing a control asks about.

`Rules` — `required`, `minLength`, `maxLength`, `email`, `pattern`, `digits`, `matching`, `checked`,
`chosen`, `anyOf`, `inRange`. A format rule passes a blank value, so an optional field with a format
is one rule rather than a special case; `and` reports the first complaint, which is why
`required() and email()` is the right order and the reverse is not.

**No validation library is required.** A rule is a function, so Konform or anything else is an
adapter: `Validation { value -> konform.validate(value).errors.firstOrNull()?.message }`.

| Component | Parameters | Story |
| --- | --- | --- |
| `TextField` | `field`, `label?`, `placeholder?`, `helper?`, `secret`, `keyboardType`, `leading?`, `trailing?` | `forms/text-field` |
| `TextareaField` | `field`, `label?`, `minLines = 3`, `maxLines = 8`, `maxLength?` | `forms/textarea` |
| `SelectField` | `field`, `options`, `label?`, `placeholder`, `optionLabel` | `forms/select` |
| `Autocomplete` | `value`, `onValueChange`, `options`, `onSelect` | `forms/autocomplete` |
| `Checkbox` | `field`, `label`, `helper?` | `forms/checkbox-and-switch` |
| `Switch` | `field`, `label`, `description?` | `forms/checkbox-and-switch` |
| `RadioGroup` | `field`, `options`, `label?`, `required`, `optionLabel` | `forms/choice-groups` |
| `CheckboxGroup` | `field: FieldState<Set<T>>`, `options`, `label?` | `forms/choice-groups` |
| `SliderField` | `field`, `label?`, `range`, `steps`, `format` | `forms/slider` |
| `OtpField` | `field`, `length = 6`, `label?`, `helper?` | `forms/one-time-code` |
| `InputGroup` | `content: RowScope` | `forms/input-group` |
| `UploadField` | `onClick`, `label`, `supporting?`, `enabled` | `forms/upload` |
| `ExtendedLabel` | `text`, `required`, `optional`, `trailing?` | used by the above |
| `HelperText` | `helper?`, `error?` | used by the above |
| `FieldScaffold` | `label?`, `required`, `helper?`, `error?`, `content` | used by the above |

`TextField`, `TextareaField` and `SelectField` are Material 3's `OutlinedTextField` — M3 already
carries the floating label, the supporting text and the error colours, so wrapping it in a second
label would give the reader two. The controls M3 leaves bare — checkbox, switch, radio, slider,
OTP — get their chrome from `FieldScaffold` instead, written once.

A `Checkbox`, a `Switch` and a `RadioGroup` row are **one** toggle target each, label included:
Material 3 ships the box alone and every caller writes the same `Row`, most of them hanging the
click on the box and handing a screen reader an unlabelled control beside some text.

`OtpField` is one field wearing several boxes, not one field per digit. Paste works, backspace
works, autofill lands in one place, and a screen reader gets a single input.

`UploadField` is chrome: a dashed well, a label and a hint. The host picks the file — there is no
one picker on every platform — so `onClick` is the application's.

`CheckboxGroup` holds the set of what is ticked rather than a list of booleans parallel to the
options, so the field holds the answer and reordering the options cannot silently change it.

## Navigation

The chrome around a screen. Bar, rail and drawer are one list of destinations; the suite picks
which. Tabs, search and a segmented control wrap Material 3. Breadcrumb and stepper are built here
because M3 has neither.

| Component | Parameters | Story |
| --- | --- | --- |
| `AppBar` | `title`, `size = Small`, `navigationIcon?`, `onNavigation?`, `actions` | `navigation/app-bar` |
| `Search` | `query`, `onQueryChange`, `placeholder`, `active`, `results` | `navigation/search` |
| `NavigationSuite` | `destinations`, `selected`, `onSelect`, `primaryAction?`, `content` | `navigation/navigation-suite` |
| `Tabs` | `labels`, `selected`, `onSelect`, `icons?`, `scrollable = false` | `navigation/tabs` |
| `SegmentedControl` | `options`, `selected`, `onSelect` | `navigation/segmented-control` |
| `FloatingToolbar` | `expanded`, `leading?`, `trailing?`, `content` | `navigation/floating-toolbar` |
| `Breadcrumb` | `items: List<BreadcrumbItem>` | `navigation/breadcrumb` |
| `Stepper` | `steps`, `current`, `onStep?`, `collapseBelow = 520.dp` | `navigation/stepper` |

`AppBarSize` — `Small`, `Centered`, `Medium`, `Large`, mapped onto M3's four top app bars.

`NavigationDestination` is a label and an icon, then the decorations a real app actually hangs on
a destination — not only a badge and a chip:

| Decoration | Compact bar | Rail / drawer |
| --- | --- | --- |
| `badge` (count or "new") | yes — M3's badge slot | yes |
| `unread` (a dot, no count) | yes, unless `badge` is set | yes |
| `supporting` (second line) | no | yes |
| `chip` (category) | no | yes |
| `shortcut` (`⌘K`) | no | yes |
| `section` (group header) | no | yes, when it changes |
| `avatar` / `picture` | yes — replaces the vector | yes |
| `busy` (spinner over the leading) | yes | yes |
| `tone` (tints the vector) | yes | yes |

A `badge` wins over `unread`: a count is more specific than a dot. Compact keeps only what fits
the bar's badge slot and the leading; everything that needs a labelled row waits for a rail.

`NavigationSuite` is `NavigationSuiteScaffold`. The caller never writes a `when` on width: compact
is a short bar, a tabletop or short window is a medium bar, anything wider is a collapsed wide
rail. `primaryAction` is the FAB the suite places in the rail header or above the bar.

A `Breadcrumb` of more than four items collapses the middle behind a menu. The last crumb is never
a control. A `Stepper` only lets a completed step be pressed, so it cannot skip ahead on a tap;
below `collapseBelow` it stacks.

### Layout

| Component | Parameters | Story |
| --- | --- | --- |
| `ResponsiveGrid` | `items`, `minSize = 200.dp`, `item` | `layout/responsive-grid` |
| `ScrollToTop` | `listState`, `after = 2` | `layout/scroll-to-top` |
| `LoadMoreButton` | `hasMore`, `loading`, `onClick` | `layout/load-more` |
| `RefreshBox` | `refreshing`, `onRefresh`, `content` | `layout/refresh-box` |

`ResponsiveGrid` is `LazyVerticalGrid` with `GridCells.Adaptive`. `ScrollToTop` sits in a `Box`
over a list and only appears once the reader has left the top. `RefreshBox` is M3's
`PullToRefreshBox`.

### Navigation 3 scenes

| Component | Parameters | Story |
| --- | --- | --- |
| `AdaptiveNavDisplay` | `backStack`, `onBack`, `entryProvider` | `navigation/list-detail` |
| `ListDetail.list / detail / extra` | metadata maps for the three panes | `navigation/list-detail` |
| `SupportingPane.main / supporting / extra` | metadata maps for the inspector layout | — |

`AdaptiveNavDisplay` is Navigation 3's `NavDisplay` with Material 3 Adaptive's list-detail and
supporting-pane strategies already installed, in that order. Compact windows fall through to a
single pane; expanded windows show both. Entries opt in with `metadata = ListDetail.list()` (or
`.detail()`, or `SupportingPane.supporting()`). The list's empty detail is this library's
`EmptyState`, not a blank pane.

The caller owns the back stack (`mutableStateListOf` is enough). Transitions are a fade on the
effects axis — Adaptive already owns the spatial motion of the panes.

## Surfaces

| Component | Parameters | Story |
| --- | --- | --- |
| `ConfirmDialog` | `visible`, `title`, `text`, `onConfirm`, `onDismiss`, `destructive` | `surfaces/confirm-dialog` |
| `Sheet` | `visible`, `onDismiss`, `content` | `surfaces/sheet` |
| `Drawer` | `open`, `onDismiss`, `drawer`, `content` | `surfaces/drawer` |
| `Tooltip` | `text`, `content` | `surfaces/tooltip` |
| `HoverCard` | `title`, `text`, `action?`, `onAction?`, `content` | `surfaces/hover-card` |
| `Menu` | `expanded`, `onDismiss`, `items` | `surfaces/menu` |
| `ContextMenu` | `items`, `content` | `surfaces/context-menu` |
| `Progress` | `progress: Float? = null`, `kind = Linear` | `surfaces/progress` |
| `LoadingMark` | `progress: Float? = null` | `surfaces/loading-mark` |
| `Toaster` / `rememberToasterState` | `show(text, tone)`, stacked | `surfaces/toast` |
| `Accordion` | `items`, `expanded: Int?`, `onExpandedChange` | `surfaces/accordion` |
| `Carousel` | `count`, `peek = 48.dp`, `page` | `surfaces/carousel` |
| `SwipeActions` | `onDismiss`, `background`, `content` | `surfaces/swipe-actions` |

`ConfirmDialog`, `Sheet` and `Drawer` wrap M3. `Toaster` is a stack of M3 `Snackbar`s painted with
`Tone` — M3's host holds one, a dashboard often needs two. `Accordion` and `Carousel` are built
here: M3 has no accordion, and the pager is Foundation's with a peek so the next card is visible.
`LoadingMark` is M3's morphing `LoadingIndicator` — use it when the wait *is* the content;
`Progress` is the spinner attached to a control.

## Date and time

| Component | Parameters | Story |
| --- | --- | --- |
| `DateField` | `value: LocalDate?`, `onValueChange`, `label` | `date-and-time/date-field` |
| `DateRangeField` | `start`, `end`, `onValueChange` | `date-and-time/date-range-field` |
| `TimeField` | `value: LocalTime?`, `onValueChange`, `label` | `date-and-time/time-field` |
| `Calendar` | `value: LocalDate?`, `onValueChange` | `date-and-time/calendar` |

The fields are how a form asks; the pickers stay in a dialog. `Calendar` is the same `DatePicker`
inline, for a page that *is* a calendar. Values are `kotlinx.datetime.LocalDate` / `LocalTime`.

## Data

| Component | Parameters | Story |
| --- | --- | --- |
| `CommandPalette` | `visible`, `query`, `items`, `onDismiss` | `data/command-palette` |
| `DataTable` | `columns`, `rows`, `collapseBelow = 600.dp` | `data/data-table` |
| `Description` | `items: List<DescriptionItem>` | `data/description` |
| `EntityHeader` | `title`, `supporting?`, `leading?`, `actions` | `data/entity-header` |
| `Pagination` | `hasPrevious`, `hasNext`, `onPrevious`, `onNext` | `data/pagination` |
| `Timeline` | `items: List<TimelineItem>` | `data/timeline` |

`DataTable` is a table in expanded panes and a stack of `Description` cards below `collapseBelow`,
measured on the offered width. `Pagination` is previous/next for a keyset page, not `page=3`.
`CommandPalette` filters by a case-insensitive contains; the host opens it (typically ⌘K).

## Media

| Component | Parameters | Story |
| --- | --- | --- |
| `Avatar` | `name`, `image?`, `tone?`, `size = 40.dp` | `media/avatar` |
| `StrangeImage` | `model`, `description` | — |
| `Gallery` | `images`, `onSelect?` | — |
| `Lightbox` | `visible`, `model`, `onDismiss`, `onPrevious?`, `onNext?` | `media/lightbox` |
| `VideoSurface` / `PdfSurface` / `CameraSurface` | `content` slot, `overlay`, `ratio` | `media/video-surface` |

`Avatar` shows initials when there is no image, and an optional [Tone] ring. `StrangeImage` is Coil
with this library's `Skeleton` / `EmptyState`. Video, PDF and camera are **chrome**: the host fills
the slot with a renderer, so `Button` never pays for Media3, PdfRenderer or CameraX.

## Motion helpers

| Helper | What it does | Story |
| --- | --- | --- |
| `Modifier.shimmer()` | The sweeping highlight `Skeleton` is built from | `display/skeleton` |
| `Modifier.animateStagger(index)` | Rises and fades a list item in, offset by its position | `screens/orders-screen` |

`animateStagger` runs **two** animations, not one: the rise is spatial and the fade is effects. A
spatial spring overshoots by design, and an alpha past 1 is at best clamped — the two axes are not
interchangeable. `motion/spatial-and-effects` shows the difference.

`Skeleton`'s sweep names its own cadence rather than taking one from the theme: it is a loop, not a
state change, and `MotionScheme` has no spec for something that never settles.

## The whole screen

`screens/sign-up-form` is the forms phase's acceptance criterion: eight controls, cross-field
validation, errors that wait their turn, a submit button that knows whether it may be pressed, and a
success banner — with one `remember` in the whole file, for business state a real screen would own
too. No `onValueChange`, no error booleans, no `touched` flags.

`screens/orders-screen` is the first phase's, and is not a demonstration either. It is a header, an alert,
a filter row, and a list that switches between loading, empty and loaded — written with no
`animate*AsState`, no transition, no interaction source, and no remembered hover, press or
visibility. Its one `remember` is the selected filter, which is business state a real application
would own too.

If a future phase makes that file need plumbing, the phase is not finished.
