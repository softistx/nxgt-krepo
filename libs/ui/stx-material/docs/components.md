# Components

Every component the library ships, what it takes, and where to see it. **This is the file a new
component is documented in**, in the change that adds it.

Open the catalogue with `./kotlin run -m md-desktop`; the story id is the line under each story's
title. Every component takes `modifier: Modifier`, and every *interactive* one takes
`style: Style = Style`. The tables leave out `modifier`, and list `style` only where its position
matters or the default is not obvious.

Story ids such as `surfaces/menu` or `feedback/skeleton` are catalogue ids (group, then story), not packages.
A section is a package: the foundation ones live under `com.softistx.material.foundation.*` and
the component families under `com.softistx.material.components.*`, in the same folders under `src/`.

**Most of these are Material 3's components, dressed.** `Button` is M3's `Button`, `Chip` is its
`FilterChip`, `Card`, `ListTile` and `StatusBadge` are its `Card`, `ListItem` and `Badge`. What is
added is the default that was missing: the colour matrix resolved into M3's own `*Colors`, the
padding and rhythm inside a card, a hover state M3's chip does not have, and a press that gives
under the finger. Only `Alert`, `EmptyState`, `Skeleton`, `ResponsiveButton`, `Rating`, `Stat`,
`LabeledDivider`, `FilterBar`, `UploadField`, `StatusDot`, `Kbd`, `QuantityField`, `AvatarGroup`,
`InlineEdit`, `ExpandableText`, `CodeBlock`, `SectionHeader`, `RelativeTime`, `ConfirmButton`,
`PasswordMeter`, `Disclosure` and `SelectionBar` are built from primitives, because M3 has nothing
to start from. AGENTS.md's *Building a component* is the rule.

## Foundation

Package `com.softistx.material.foundation.theme` and `com.softistx.material.foundation.style`.

| Story | Shows |
| --- | --- |
| `foundation/typography` | The 20 `TypographyVariant` roles at each `Emphasis` |
| `foundation/spacing` | The eight-step spacing scale, to scale |
| `foundation/shapes` | Material 3's eight shape slots, expressive ones included |
| `foundation/icons` | The library's icon set |
| `foundation/semantic-colours` | `success` / `info` / `warning` / `error`, main and container |

## Motion

Package `com.softistx.material.foundation.motion`.

| Story | Shows |
| --- | --- |
| `motion/spatial-and-effects` | The two axes side by side — the square overshoots, the swatch does not |
| `motion/every-speed-at-once` | `Fast` / `Default` / `Slow` on the same move |

The header's **Motion** control switches the whole tree between `MotionScheme.expressive()`,
`MotionScheme.standard()` and off. Off stops this library's motion; Material 3's own components
keep their built-in animations, because a `MotionScheme` has no null.

With no `motionScheme` and no `motion`, `StxTheme` follows the OS: "Reduce motion" installs a scheme whose
every spec is a snap and an `StxMotion` with `enabled = false`. **Pass either one and the OS is not
read**: `motionScheme` alone keeps motion on with your scheme, `motion` alone keeps the expressive scheme.
`StxThemeProvider` forwards the same rule (its `motionScheme` is `null` by default). Android reads the
animator scale and iOS `UIAccessibilityIsReduceMotionEnabled`. **Desktop follows only
`-Dstx.material.reduceMotion=true|false`** (absent means not reduced; no process is spawned). An app
that wants the OS's answer opts in with `readDesktopReducedMotion()` (`suspend`, on `Dispatchers.IO`;
`null` is unknown; `defaults` on macOS, `gsettings` on GNOME, Windows is not readable) and passes it in:

```kotlin
var reduced by remember { mutableStateOf(false) }
LaunchedEffect(Unit) { reduced = readDesktopReducedMotion() == true }
StxTheme(motion = StxMotion.system(reduced)) { App() }
```

`StxMotion.system(reduced)` snaps this library's motion; add `motionScheme` to snap M3's own morphs too.
The read is taken when the theme is composed, not observed. See `docs/troubleshooting.md`.

## Text

Package `com.softistx.material.foundation.text`.

| Component | Parameters | Story |
| --- | --- | --- |
| `Typography` | `text`, `variant = BodyMedium`, `emphasis = Full`, `color`, `align`, `decoration`, `maxLines`, `overflow`, `emphasized = false` | `foundation/typography` |

`TypographyVariant` is the 15 Material 3 roles plus five this library adds: `Overline`, `Caption`,
`Metric` (a figure meant to be read at a glance), `Code` (monospaced), `Link` (underlined).

`Emphasis` — `Full` (1.0), `Medium` (0.80), `Subtle` (0.72) — is an alpha on the content colour, so
secondary text stays in the palette instead of picking a grey. `Subtle` holds 4.5:1 (AA for body
text) on `surface`, `surfaceContainer` and `surfaceContainerHigh`, light and dark; `docs/tokens.md` has the ratios.

`emphasized` is the other axis and does not touch colour: it picks the Material 3 `*Emphasized`
style of the same role (15 of them), the heavier weight for the one line a screen wants read first.
`emphasis` says how *loud the colour* is, `emphasized` how *heavy the type* is; they combine.
`TypographyVariant.style(emphasized)` is the same mapping for a caller that needs the `TextStyle`.
The `Alert` title (`TitleSmall`), the `EmptyState` title (`TitleLarge`) and the `AppBar` title (`TitleLarge` on `Small` and `Centered`; M3's headline and display on `Medium` and `Large`) are emphasised and announced as headings.
One emphasised title per screen: `SectionHeader` (`TitleMedium`) and `EntityHeader`
(`HeadlineMedium`) wear it by default (`emphasized = false` turns it off). M3 documents the
emphasised styles as `Medium` (500), but a default `Typography()` in 1.12.0-alpha03 returns the
regular weight for most roles, so `style(true)` raises the weight itself: `Medium` for a regular
role, `SemiBold` for a role that is already `Medium`, unless the theme's own emphasised style is
heavier. `Metric` is display-sized and at least `SemiBold`: a theme whose emphasised display weight is heavier keeps it.
The five extra variants ignore the flag.

```kotlin
Typography("Results", variant = TitleLarge, emphasized = true)   // the heavier twin of TitleLarge
Text("Results", style = TitleLarge.style(emphasized = true))      // the same, as a TextStyle
SectionHeader("Results", emphasized = false)                      // opt out of the default
```

Visible defaults that changed on the guide: `SectionHeader` is now `TitleMedium` emphasised (was
`TitleSmall`), `EntityHeader` `HeadlineMedium` emphasised (was `HeadlineSmall`), `Metric` is
display-sized (was `headlineMedium`), `Stat` reserves a delta line by default
(`reserveDelta = false` gives it back), and `AppBar` titles are emphasised (`Small` and `Centered` stay `TitleLarge`, on one line; `Medium` and `Large` take M3's flexible type).

## Icons

Package `com.softistx.material.foundation.icon`.

| Component | Parameters | Story |
| --- | --- | --- |
| `Icon` | `icon: ImageVector`, `description: String?`, `size = Medium`, `tint` | `foundation/icons` |

`description` has **no default**. A caller must decide whether the icon carries meaning (a string)
or repeats the label beside it (`null`); it is the one accessibility decision that cannot be made
correctly on the caller's behalf.

`IconSize` — `Small` 16, `Medium` 20, `Large` 24, `XLarge` 32 dp.

`StxIcons` holds thirty-four hand-built vectors: `Add`, `Check`, `Close`, `ChevronLeft`,
`ChevronRight`, `ChevronDown`, `ChevronUp`, `Eye`, `EyeOff`, `Delete`, `Edit`, `Inbox`, `Person`,
`Home`, `Menu`, `MoreHoriz`, `Calendar`, `Schedule`, `Star`, `Search`, `Warning`, `Copy`, `Minus`,
`Attach`, `Info`, `ViewList`, `ViewGrid`, `Send`, `Pin`, `Touch`, `Bell`, `Printer`, `Phone`, `EditNote`. They
are defined in code because **no icon pack is reachable from here**: the Kotlin Toolchain's `$compose` catalog
has no key for the Material icons, `$compose.material` does not carry `material-icons-core` in
Compose Multiplatform 1.11, and the AndroidX icon artifacts are Android-only. An application that
wants a thousand glyphs should depend on a pack directly and pass the `ImageVector` in — every
component here takes one.

## Buttons

Package `com.softistx.material.components.button`.

| Component | Parameters | Story |
| --- | --- | --- |
| `Button` | `text`, `onClick`, `variant = Filled`, `color = Primary`, `enabled`, `size = Small` | `buttons/button` |
| `ButtonSurface` | as `Button`, plus a `RowScope` `content` slot instead of `text` | `buttons/button` |
| `IconButton` | `icon`, `description`, `onClick`, `variant = Ghost`, `color = Neutral`, `size`, `enabled`, `style` | `buttons/icon-button` |
| `ResponsiveButton` | `text`, `icon`, `onClick`, `variant`, `color`, `enabled`, `collapseBelow = 360.dp`, `size = Small` | `buttons/responsive-button` |
| `ButtonRow` | `align = End`, `content: RowScope` | `buttons/button-row` |
| `Fab` | `icon`, `description`, `onClick`, `text?`, `expanded = true`, `color = Primary`, `style` | `buttons/fab` |
| `FabMenu` | `expanded`, `onExpandedChange`, `actions: List<FabAction>` | `buttons/fab-menu` |
| `SplitButton` | `text`, `onClick`, `overflow: List<MenuItem>`, `enabled`, `style` | `buttons/split-button` |
| `ToggleButton` | `text`, `checked`, `onCheckedChange`, `variant = Tonal`, `color`, `icon?`, `enabled`, `style` (no `size`: M3's default height) | `buttons/toggle-button` |
| `IconToggle` | `icon`, `description`, `checked`, `onCheckedChange`, `checkedIcon`, `variant`, `color`, `size = IconSize.Medium` (the glyph, not the container), `enabled`, `style` | `buttons/icon-toggle` |
| `CopyButton` | `text`, `description = "Copy"` | `buttons/copy-button` |
| `ConfirmButton` | `text`, `onConfirm`, `confirmText = "Confirm?"`, `color = Danger`, `holdMs = 3000`, `size = Small` | `buttons/confirm-button` |
| `BusyButton` | `text`, `onClick`, `busy`, `variant`, `color`, `enabled`, `size = Small` | `buttons/busy-button` |
| `MoreMenu` | `items: List<MenuItem>`, `description = "More"` | `buttons/more-menu` |
| `OverflowBar` | `actions: List<OverflowAction>`, `maxVisible` | `buttons/overflow-bar` |
| `IconBadge` | `icon`, `description`, `onClick`, `count = 0`, `tone = Error` | `buttons/icon-badge` |
| `ViewToggle` | `value: ViewMode`, `onChange`, `enabled = true`, `style = Style` | `buttons/view-toggle` |

`ButtonVariant` — `Filled`, `Tonal`, `Outlined`, `Ghost`, `Link`.
`ButtonSize` — `ExtraSmall` 32 dp, `Small` 40 dp (the default), `Medium` 56, `Large` 96, `ExtraLarge` 136: M3's own
heights, each bringing its padding, label style and pressed corner.
Buttons morph, a pill at rest and squarer while pressed, so they take no press scale. So does the rest of the
family, through M3's own morphing APIs (story `buttons/morphing-family`):

- `IconButton` — `IconButtonDefaults.shapes()`, round at rest, squarer pressed; still the 40 dp container in a 48 dp target.
- `IconToggle` — `IconButtonDefaults.toggleableShapes()`, round off, squarer pressed and while checked.
- `ToggleButton` — `ToggleButtonDefaults.shapes()`, a pill off, squarer pressed and while checked.
- `SplitButton` — M3's split shapes: each half rounds its inner corners under the finger. The trailing half is a
  toggle checked while the menu is open, so it changes shape and its chevron points up.
- `Fab` — M3's FAB has no `shapes =` overload, so the library draws the morph: the corners shrink to the theme's
  `small` slot while pressed and spring back at the scheme's fast spatial speed. Rest size and shape are unchanged.

None of them takes a colour from M3's vibrant roles: the colours still come from `ButtonColor`, so the look at rest is
what it was. Every one takes `style: Style = Style` (default `StxStyles.button`).
A `Link` keeps its 40 dp height at every size instead of growing with `size`, being text in a sentence.
The collapsed `ResponsiveButton` stays an icon button, 40 × 40, at any size.
The story is `buttons/size-and-variant-grid`.

```kotlin
Button("Save", onClick, size = ButtonSize.Medium)
```

`ButtonColor` — `Primary`, `Secondary`, `Success`, `Info`, `Warning`, `Danger`, `Neutral`.

The 5 × 7 matrix is resolved in **one** function, `buttonColors(variant, color)`, which returns
Material 3's own `ButtonColors` — so M3 paints the container, the ripple and the disabled treatment,
and this library only decides *which* colours. Adding a colour touches one enum entry and one `when`
branch; see `buttons/variant-and-colour-matrix` for the whole grid at once. `buttonBorder` answers
the same way for the outline. A button's press is M3's shape morph, so `buttonStyle`, the one style of
the whole family, keeps only the disabled alpha.

The button family dropped the 0.97 press scale: the shape morph is the press feedback now, so a
`pressed { scale(0.97f) }` you relied on is gone unless you pass it back through `style`.

Every control above takes `style: Style = Style`, merged over `buttonStyle`. Pass a block, not
colour or shape parameters:

```kotlin
Fab(
    icon = StxIcons.Add,
    description = "Add",
    onClick = onAdd,
    style = Style {
        disabled { alpha(0.2f) }             // fainter than M3's 0.38
        pressed { animate(motion.effects()) { alpha(0.8f) } }
    },
)
```

On `SplitButton` the `pressed` and `hovered` blocks fire while either half is pressed or hovered.

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
trailing chevron (a toggle that turns up while the menu is open) opens a `Menu` of alternatives.

`ToggleButton` is M3's `ToggleButton` — Follow, pin, list-or-grid — and `IconToggle` is the icon
form. Unchecked is quiet; checked uses the colour pair. `CopyButton` copies and flashes a check;
it writes through `LocalClipboard.setClipEntry`. `ClipEntry` is still native per target, so a
small `expect` builds the plain-text entry.
`ConfirmButton` arms on the first click and fires on the second; wait three seconds and it
disarms. A sentence of warning still belongs on `ConfirmDialog`. `BusyButton` swaps the label
for a spinner. `MoreMenu` is the trailing more on a row. `OverflowBar` is M3's `AppBarRow`:
visible icons stay in the row, the rest land in the overflow menu. `IconBadge` is an `IconButton`
in a `BadgedBox`; count zero shows no badge. `ViewToggle` is list-or-grid as a connected button group of two icon toggles (the same body as
`SegmentedControl`: round ends, squarer seam, filled when chosen, no checkmark, `Role.RadioButton`).
`FabMenu` and `OverflowBar` take no `style`: each hosts several controls (the main button and its
actions; the row of icons and its overflow menu), and every control in them is a `Fab`, an
`IconButton` or an M3 menu item with its own.

**Collapsed, it is an `IconButton`** — round, 40 × 40, M3's own metrics — not a pill with the label
taken out. The two forms are two components and `AnimatedContent` morphs between them.
`ResponsiveButtonTest` renders it and measures the box: 40 × 40 collapsed, 130 × 40 expanded.

## Feedback

What reports state to the reader. Package `com.softistx.material.components.feedback`.

| Component | Parameters | Story |
| --- | --- | --- |
| `StatusBadge` | `text`, `tone = Info` — no `style`: a badge is inert | `feedback/status-badge` |
| `Alert` | `text`, `tone = Info`, `title?`, `visible = true`, `style = Style`, `icon: ImageVector? = AlertDefaults.icon(tone)`, `action?` | `feedback/alert` |
| `Skeleton` | `height = 16.dp`, `shape = shapes.extraSmall` | `feedback/skeleton` |
| `StatusDot` | `tone`, `description?`, `size = 8.dp` — no `style`: a dot is inert | `feedback/status-dot` |
| `AnnouncementBar` | `text`, `tone = Info`, `style = Style`, `onDismiss?`, `icon: ImageVector? = AlertDefaults.icon(tone)`, `action?` | `feedback/announcement-bar` |
| `PromoBanner` | `text`, `code?`, `tone = Success`, `style = Style`, `onDismiss?`, `icon: ImageVector? = AlertDefaults.icon(tone)` | `feedback/promo-banner` |
| `Progress` | `progress: Float? = null`, `kind = Linear` (`Circular`, `Wavy`, `WavyCircular`) | `feedback/progress` |
| `LabeledProgress` | `progress`, `caption?`, `kind` | `feedback/labeled-progress` |
| `LoadingMark` | `progress: Float? = null`, `contained = false` | `feedback/loading-mark` |
| `TypingIndicator` | `label = "Someone is typing"` | `feedback/typing-indicator` |
| `Toaster` / `rememberToasterState` | `show(text, tone)`, stacked | `feedback/toast` |

`Alert` owns its own enter and exit — pass `visible` and it animates itself; there is no
`AnimatedVisibility` for the caller to write. `Skeleton` shimmers on its own.

`Alert`, `AnnouncementBar` and `PromoBanner` share one surface (`StxStyles.alert(tone)`): a tonal
`surfaceContainerHigh` with no outline and a `largeIncreased` (20 dp) corner, except `Tone.Error`,
which keeps the error container because it is the one tone that must interrupt. The tone lives on
a leading 36 dp `MaterialShapes.Cookie6Sided` plate in the tone's solid pair (inverted on an error, to hold 3:1 on its container), carrying
`AlertDefaults.icon(tone)` (check, info, warning). `icon` replaces that icon and `icon = null`
drops the plate. `icon` sits before the trailing `action` lambda, so `Alert("x") { … }` still
compiles. The tone is a plate, not a slab.

## Content

What the screen is about. Package `com.softistx.material.components.content`.

| Component | Parameters | Story |
| --- | --- | --- |
| `Card` | `variant = Filled`, `onClick: (() -> Unit)? = null`, `enabled`, `style = Style`, `content: ColumnScope` | `content/card` |
| `Chip` | `text`, `selected`, `onClick?`, `enabled`, `style = Style`, `leading?`, `trailing?` | `content/chip` |
| `ListTile` | `title`, `supporting? = null`, `onClick: (() -> Unit)? = null`, `enabled = true`, `style = Style`, `leading? = null`, `trailing? = null`, `selected: Boolean? = null`, `group: ListTileGroup? = null` | `content/list-tile` |
| `EmptyState` | `title`, `description?`, `illustration?`, `action?` | `content/empty-state` |
| `Stat` | `value`, `label`, `delta?` (a plain line; only Success, Warning and Error add a dot), `tone = Info`, `reserveDelta = true` | `content/stat` |
| `FilterBar` | `options`, `selected`, `onChange`, `style = Style` | `content/filter-bar` |
| `Rating` | `value`, `onChange`, `max = 5`, `enabled`, `style = Style` | `content/rating` |
| `LabeledDivider` | `label` — no `style`: a divider is inert | `content/labeled-divider` |
| `ActionChip` | `text`, `onClick`, `enabled`, `style = Style`, `leading?`, `trailing?` | `content/action-chip` |
| `Kbd` | `keys: List<String>` — no `style`: a keycap is inert | `content/keyboard-shortcut` |
| `SuggestionChip` | `text`, `onClick`, `enabled`, `style = Style`, `leading?` | `content/suggestion-chip` |
| `FileChip` | `name`, `sizeLabel?`, `enabled`, `onClick?`, `onRemove?`, `style = Style` | `content/file-chip` |
| `SectionHeader` | `title`, `supporting?`, `action?`, `emphasized = true` — no `style`: a header is inert | `content/section-header` |
| `CodeBlock` | `text`, `copyable = true`, `style = Style` | `content/code-block` |
| `ExpandableText` | `text`, `collapsedLines = 3`, `more`, `less`, `style = Style` | `content/expandable-text` |
| `ReactionBar` | `reactions: List<Reaction>`, `style = Style` | `content/reaction-bar` |
| `QuoteBlock` | `text`, `attribution?` | `content/quote-block` |
| `LinkPreview` | `title`, `url`, `description?`, `onClick?`, `style = Style`, `leading?` | `content/link-preview` |
| `MessageBubble` | `text`, `outgoing`, `name?`, `meta?`, `style = Style`, `onClick?` | `content/message-bubble` |
| `Comment` | `name`, `text`, `supporting?`, `trailing?` | `content/comment` |
| `ReplyPreview` | `name`, `text`, `onDismiss?`, `style = Style` | `content/reply-preview` |
| `PinBar` | `text`, `onClick?`, `onDismiss?`, `style = Style` | `content/pin-bar` |
| `MentionChip` | `name`, `onClick`, `image?`, `enabled`, `onRemove?`, `style = Style` | `content/mention-chip` |
| `Price` | `amount`, `compareAt?`, `period?`, `tone?` | `content/price` |
| `FeatureList` | `items: List<FeatureItem>` | `content/feature-list` |
| `PricingCard` | `name`, `amount`, `features`, `highlighted`, `badge?`, `action?` — no `style`: the plan is inert, its `action` is the control | `content/pricing-card` |

`CardVariant` — `Filled`, `Outlined`, `Elevated`.

`ListTileGroup(index, count)` — `count >= 1` and `index` in `0 until count`, otherwise it throws.
`ListTileDefaults.GroupGap` — the vertical space M3 puts between the tiles of a segmented list.

A `Card` is interactive **iff** `onClick` is not null: it grows a hover and a press state only when
it does something, and it is announced as a button. `ListTile` follows the same rule.

Every interactive part of the family takes `style = Style`, placed before a trailing lambda. The
defaults are the entries that already exist: `StxStyles.card` (0.99, a clickable `Card`, `PinBar`,
`LinkPreview`) and `StxStyles.chip` (0.97, `MentionChip` and `FileChip`). `FilterBar`, `ReactionBar`
and `Rating` hand their `style` to every chip or star, `ExpandableText` to its toggle, `CodeBlock`
to its copy button and `ReplyPreview` to its dismiss button. One rule for a host: when its
interactive part is a single control it forwards `style` to it; a host with several controls takes
none and is listed below. A `FileChip`
without `onClick` is a receipt and takes no press scale. `SectionHeader` and the titles of
`PricingCard` and `DangerZone` are announced as headings.

The parts that take no `style` are not interactive: `LabeledDivider`, `Kbd`, `Stat`, `QuoteBlock`,
`Price`, `FeatureList`, `SectionHeader` and `EmptyState`. `PricingCard` (its `action` slot) and
`Comment` (its `trailing` slot) host the caller's own controls.

A `ListTile` is M3's `ListItem`, so the press is a shape morph (4 dp at rest, 16 dp pressed) and takes
no scale. With `selected = null` an interactive tile is a button. With `selected = true` or `false` it
is a single choice, as a list-detail's current row is: the chosen row takes M3's selected tone and is
announced as a radio button that is or is not selected. `group = ListTileGroup(index, count)` makes it
one tile of a segmented list (`SegmentedListItem`): round at the top of the first and the bottom of the
last, squarer between, space them by `ListTileDefaults.GroupGap`. `selected` and `group` need `onClick` (M3 has no static segmented item), and a tile given either without one
is refused with an `IllegalArgumentException`.

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

`EmptyState` fades in on its own, so the caller writes no `AnimatedVisibility`: it starts hidden
and targets visible (`AnimatedVisibility(visible = true)` is already shown on its first
composition and never animates). For a picture made of one icon, pass
`illustration = { EmptyStateDefaults.IconPlate(StxIcons.Inbox) }`: a 96 dp
`MaterialShapes.SoftBurst` plate on `secondaryContainer`. `EmptyState` has no `icon` parameter, so
the one `illustration` slot stays the only way to give it a picture.

`Stat` is a `Card` wearing `TypographyVariant.Metric`; it reserves the height of a delta (`reserveDelta = true`)
so stats in a row keep the same height with or without one. `FilterBar` is a row of `Chip`s — not the
filter subsystem of phase 7, a selected subset of names. The empty set is "everything". `Rating`
is a row of `IconButton`s; M3 has no rating control. `LabeledDivider` is M3's `HorizontalDivider`
with a word in the gap.

`Chip` is a filter (it stays selected). `ActionChip` is a verb — M3's `AssistChip` — so "Call" does
not wear a tick. `StatusDot` is presence next to a name; an `Avatar` already has a tone ring for
the same fact on a face. `Kbd` draws a shortcut as keycaps.

`SuggestionChip` is a completion, not a filter and not a verb. `FileChip` is what landed in an
`UploadField`. `SectionHeader` titles a block (`EntityHeader` titles a page). `CodeBlock` is
`TypographyVariant.Code` plus `CopyButton`. `ExpandableText` only grows a "Read more" when the
paragraph actually overflows.

`ReactionBar` is a row of `Chip`s; `toggleReaction` is the usual count arithmetic and never goes
below zero. `AnnouncementBar` is the strip at the top of a page — `Alert` is a paragraph in the
body; missing `onDismiss` means the bar cannot be put away. `QuoteBlock` is M3's `VerticalDivider`
plus the words. `LinkPreview` is chrome around a URL: the library does not fetch Open Graph,
`leading` is the thumbnail the host already has.

`MessageBubble` is a chat turn — incoming on the start edge, outgoing in `primaryContainer` on the
end. A `ListTile` is a row in a list. `Comment` is the named paragraph under an article, with a
trailing slot for a `ReactionBar`. `ReplyPreview` sits above a `Composer`; `QuoteBlock` is a
passage in the body. `PinBar` is a message the room chose to keep; `AnnouncementBar` is an
incident. `MentionChip` is who was @-named (`FileChip` is what landed in an upload).

`Price` is an already-formatted amount (`"€12"`), optional compare-at (struck through) and period
(`/mo`). A `Stat` is a dashboard figure. `FeatureList` is a receipt of what a plan includes, not
a form. `PricingCard` is name + `Price` + `FeatureList` + the CTA; `highlighted` lifts it.
`PromoBanner` is an `AnnouncementBar` with a code and a `CopyButton`.

## Forms

Package `com.softistx.material.components.form` (state and rules in `com.softistx.material.components.form.state`).

The part of the library that removes the most work, because a form is where plumbing usually lives.
A form is **one value** — the request body, the GraphQL input — held by a `FormState<T>`. A control
takes the plain `value` and `onValueChange` of the property it edits, plus `isError` and
`supportingText` (or `supportingText` alone on the groups); the form supplies each from that one
value, and *when the error is allowed to appear* is the form's decision, not the control's.

```kotlin
class SignUpForm : FormState<SignUpInput>(
    SignUpInput(email = "", terms = false),
    listOf(SignUpInput::email, SignUpInput::terms),
) {
    override val validate = Validation {
        SignUpInput::email { required(); email() }
        SignUpInput::terms { checked() }
    }
}

val form = rememberForm { SignUpForm() }

TextField(
    value = form.value.email,
    onValueChange = { v -> form.update(SignUpInput::email) { it.copy(email = v) } },
    label = "Email",
    isError = form.hasError(SignUpInput::email),
    supportingText = form.error(SignUpInput::email),
)
Checkbox(
    value = form.value.terms,
    onValueChange = { v -> form.update(SignUpInput::terms) { it.copy(terms = v) } },
    label = "I accept the terms",
    supportingText = form.error(SignUpInput::terms),
)
Button("Create account", enabled = form.isValid(), onClick = { form.submit { register(it) } })
```

| Type | What it holds | Where it lives |
| --- | --- | --- |
| `FormState<T>` | the form as one value: `value`, `errors`, `error(path)`, `hasError(path)`, `isValid()`, `isDirty()`, `update(path) { }`, `validateAll()`, `submit { }`, `clearErrors()`, `reset()` | subclass it, declare `validate`, and `rememberForm { MyForm() }` |
| `Validation<T>` | the rules, declared once over the whole of `T` — Konform's type, exported | `override val validate = Validation { ... }` |

**An error is held until it is earned.** A required field is invalid the moment an empty form is
drawn, and showing that immediately greets someone with six complaints about work they have not
started. So `errors` holds only the paths that have been `update`d, or that `validateAll()` /
`submit { }` demanded. `isValid()` checks everything without publishing anything, so a submit
button reads it and is disabled from the first frame while the form stays quiet.

**The rules are Konform's.** `FormState` takes a `Validation<T>` and the library adds constraints to
its builder — `required`, `email`, `minLength`, `maxLength`, `pattern`, `digits`, `matching`,
`checked`, `chosen`, `anyOf`, `inRange`. Each takes its message as a parameter (with an English
default), so a caller with a string catalogue passes translated text in. A format rule passes a
blank value, so an optional field with a format is one rule rather than a special case;
`required(); email()` reports "required" for an empty box, which is why that order is the right one.
`matching` needs the whole value, so it sits in `dynamic(SignUpInput::confirm) { form ->
matching(form.password) }`.

| Component | Parameters | Story |
| --- | --- | --- |
| `TextField` | `value`, `onValueChange`, `label?`, `placeholder?`, `helper?`, `secret`, `keyboardType`, `leading?`, `trailing?`, `style = Style` | `forms/text-field` |
| `TextareaField` | `value`, `onValueChange`, `label?`, `minLines = 3`, `maxLines = 8`, `maxLength?`, `style = Style` | `forms/textarea` |
| `SelectField` | `value`, `onValueChange`, `options`, `label?`, `placeholder`, `isError`, `supportingText?`, `enabled`, `style = Style`, `optionLabel` | `forms/select` |
| `Autocomplete` | `value`, `onValueChange`, `options`, `onSelect`, `label?`, `placeholder`, `enabled`, `isError`, `supportingText?`, `style = Style`, `optionLabel` | `forms/autocomplete` |
| `Checkbox` | `value`, `onValueChange`, `label`, `helper?`, `supportingText?`, `enabled`, `style = Style` | `forms/checkbox-and-switch` |
| `TriStateCheckbox` | `state: CheckState`, `onClick`, `label`, `helper?`, `enabled`, `style = Style` | `forms/tri-state-checkbox` |
| `Switch` | `value`, `onValueChange`, `label`, `description?`, `enabled`, `style = Style` | `forms/checkbox-and-switch` |
| `RadioGroup` | `value`, `onValueChange`, `options`, `label?`, `required`, `enabled`, `style = Style`, `optionLabel` | `forms/choice-groups` |
| `CheckboxGroup` | `value: Set<T>`, `onValueChange`, `options`, `label?`, `enabled`, `style = Style`, `optionLabel` | `forms/choice-groups` |
| `SliderField` | `value: Float` or `ClosedFloatingPointRange<Float>`, `label?`, `range`, `steps`, `style = Style`, `format` | `forms/slider`, `forms/range-slider` |
| `TagField` | `tags`, `onTagsChange`, `label?`, `placeholder`, `style = Style` | `forms/tags` |
| `QuantityField` | `value`, `onValueChange`, `range = 0..999`, `label?`, `style = Style` | `forms/quantity` |
| `OtpField` | `value`, `onValueChange`, `length = 6`, `label?`, `helper?`, `style = Style` | `forms/one-time-code` |
| `InputGroup` | `content: RowScope` | `forms/input-group` |
| `UploadField` | `onClick`, `label`, `supporting?`, `enabled`, `style = Style` | `forms/upload` |
| `InlineEdit` | `value`, `onValueChange`, `placeholder`, `enabled`, `variant`, `style = Style` | `forms/inline-edit` |
| `PasswordMeter` | `value` | `forms/password-meter` |
| `CopyField` | `value`, `label?`, `helper?`, `style = Style` | `forms/copy-field` |
| `ThemeToggle` | `value: ColorMode`, `onChange`, `enabled`, `style = Style` | `forms/theme-toggle` |
| `FormSection` | `title`, `supporting?`, `content` | `forms/form-section` |
| `DangerZone` | `text`, `title = "Danger zone"`, `action` | `forms/danger-zone` |
| `Composer` | `value`, `onValueChange`, `onSend`, `onAttach?`, `sendEnabled`, `style = Style` | `forms/composer` |
| `Checklist` | `items: List<CheckItem>`, `onToggle`, `enabled`, `style = Style` | `forms/checklist` |
| `ExtendedLabel` | `text`, `required`, `optional`, `trailing?` | used by the above |
| `HelperText` | `helper?`, `error?` | used by the above |
| `FieldScaffold` | `label?`, `required`, `helper?`, `error?`, `content` | used by the above |

Every interactive part takes `style: Style = Style`, applied last so the caller's wins, and puts it
just before a trailing lambda (`optionLabel`, `format`) so `RadioGroup(…) { it.label }` still compiles.
The defaults are named in `StxStyles`: `field` (`TextField`, `TextareaField`, `Autocomplete` and
`SelectField`: empty, because M3 already dims them), `dimmed` (`OtpField` and `InlineEdit` at rest:
the 0.38 disabled fade M3 does not give them), `toggle` (the rows of `Checkbox`, `TriStateCheckbox`,
`Switch`, `RadioGroup` and `CheckboxGroup`: a 0.99 press scale) and `upload` (`UploadField`: a 0.99
press scale and the disabled fade). `CopyField`, `Composer`, `TagField`, `ThemeToggle` and `Checklist` hand their `style` to the
field or the rows they are made of.

The parts that take no `style` are not interactive: `FormSection`, `DangerZone`, `InputGroup`,
`PasswordMeter`, `ExtendedLabel`, `HelperText` and `FieldScaffold`.

The exceptions, each because Material 3 imposes its own `*Defaults`:

- `SliderField` (both overloads): takes `style = Style` but has no default look (no `StxStyles`
  entry). M3's `Slider` and `RangeSlider` paint and
  dim the track and the thumb themselves, and a press scale would fight the drag.
- `QuantityField`: takes `style = Style` but has no default look (no `StxStyles` entry). Its two buttons are `IconButton`s that carry the button
  family's style and dim themselves, so a fade on the row would dim them twice.
- `Checkbox`, `TriStateCheckbox`, `Switch`, `RadioGroup` and `CheckboxGroup` have no disabled fade in
  `toggle`: M3's box, thumb and dot dim themselves.
- The text fields keep M3's container, outline, label and error colours (`TextFieldDefaults`): the
  `field` style is empty (a fade would dim a disabled field twice), and a `background` in a caller's
  style paints behind M3's transparent container.

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

`TagField` is M3's `InputChip` plus a draft field: confirm, a trailing comma or Enter adds, and
backspace on an empty draft removes the last tag. Duplicates are ignored, case insensitive.
`QuantityField` is plus and minus around a metric; M3 has no stepper. `SliderField` has a second
overload for a `ClosedFloatingPointRange` — M3's `RangeSlider`, with both ends printed.

`InlineEdit` keeps a draft until it is committed, so Escape can put the previous value back.
`PasswordMeter` is four segments graded locally (length, case, digit, symbol) — not a breach
check. `CopyField` is a read-only field with a `CopyButton`. `TriStateCheckbox` is the parent of
a group: Off, On, or Indeterminate; `cycleCheckState` is the usual next value.
`ThemeToggle` names Light / Dark / System; the host still installs the scheme. `FormSection` is a
`SectionHeader` plus its fields. `DangerZone` is an outlined card with the error colour on the
title, so a delete is not just another section.

`Composer` is a `TextareaField` (1–4 lines) with send, and attach when `onAttach` is set. Return
still inserts a newline; send is the button. `Checklist` is a column of `Checkbox` rows;
`toggleCheckItem` flips one index. Both hold labelled items rather than a parallel `List<Boolean>`,
the same rule as `CheckboxGroup`.

`CheckboxGroup` holds the set of what is ticked rather than a list of booleans parallel to the
options, so the field holds the answer and reordering the options cannot silently change it.

## Navigation

Package `com.softistx.material.components.navigation`.

The chrome around a screen. Bar, rail and drawer are one list of destinations; the suite picks
which. Tabs and search wrap Material 3; a segmented control is its connected button group of toggle buttons. Breadcrumb and stepper are built here
because M3 has neither.

| Component | Parameters | Story |
| --- | --- | --- |
| `AppBar` | `title`, `size = Small`, `navigationIcon?`, `navigationDescription?`, `onNavigation?`, `scrollBehavior: TopAppBarScrollBehavior? = null`, `actions` | `navigation/app-bar` |
| `Search` | `query`, `onQueryChange`, `placeholder`, `active`, `results` | `navigation/search` |
| `NavigationSuite` | `destinations`, `selected`, `onSelect`, `primaryAction?`, `content` | `navigation/navigation-suite` |
| `Tabs` | `labels`, `selected`, `onSelect`, `icons?`, `scrollable = false`, `enabled`, `style` | `navigation/tabs` |
| `SegmentedControl` | `options`, `selected`, `onSelect`, `enabled = true`, `style = Style`, `icons: List<ImageVector>? = null` | `navigation/segmented-control` |
| `FloatingToolbar` | `expanded`, `leading?`, `trailing?`, `content` | `navigation/floating-toolbar` |
| `Breadcrumb` | `items: List<BreadcrumbItem>` | `navigation/breadcrumb` |
| `Stepper` | `steps`, `current`, `onStep?`, `collapseBelow = 520.dp` | `navigation/stepper` |
| `StepFooter` | `onNext`, `onBack?`, `nextLabel`, `busy` | `navigation/step-footer` |
| `BottomBar` | `actions`, `fab?` | `navigation/bottom-bar` |

`AppBarSize` — `Small`, `Centered`, `Medium`, `Large`. `Medium` and `Large` are M3's flexible bars (headline-medium and display-small
titles; expanded 112 dp and 120 dp, where the plain bars were 112 dp and 152 dp). The title of every size is emphasised and announced as a heading.
A `scrollBehavior` makes the bar react to the scrolling content: `Medium` and `Large` collapse to the 64 dp small bar
(`exitUntilCollapsedScrollBehavior()`), `Small` and `Centered` slide away and return on the first scroll back (`enterAlwaysScrollBehavior()`).
The bar is only told about the scroll through `nestedScroll`:

```kotlin
val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Column(Modifier.nestedScroll(behavior.nestedScrollConnection)) {
    AppBar("Orders", size = AppBarSize.Large, scrollBehavior = behavior)
    LazyColumn { /* … */ }
}
```

Each tab has its own interaction source, so its press style runs.
`Stepper` marks each step with a 32 dp plate: a circle, or a `Cookie9Sided` for the current step, morphing on `motion.spatial` as the step advances.
`StepFooter` is Back (optional) and Continue under a stepper; `busy` turns Continue into a
`BusyButton`. `BottomBar` is M3's `BottomAppBar` — verbs for this screen, not destinations
(`NavigationSuite` is the destinations). `StepFooter` and `NavigationSuite` take no `style`: they
host several controls (Back and Continue; the destinations and the primary action), each with its
own. `AppBar` takes none either (its navigation `IconButton` and `actions` slot keep their own).

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

A compact bar holds three to five destinations (M3's rule); past that, labels truncate to one line
with an ellipsis, so move the extra destinations behind a "More" entry, a drawer or the rail.
`NavigationSuiteDefaults.CompactBelow` (600 dp) is the width under which the bar is compact, for an
app that decides which destinations to show.

A `Breadcrumb` of more than four items collapses the middle behind a menu. The last crumb is never
a control. A `Stepper` only lets a completed step be pressed, so it cannot skip ahead on a tap;
below `collapseBelow` it stacks.

### Layout

Package `com.softistx.material.components.layout`.

| Component | Parameters | Story |
| --- | --- | --- |
| `ResponsiveGrid` | `items`, `minSize = 200.dp`, `item` | `layout/responsive-grid` |
| `ScrollToTop` | `listState`, `after = 2`, `style = Style` | `layout/scroll-to-top` |
| `LoadMoreButton` | `hasMore`, `loading`, `onClick`, `label`, `style = Style` | `layout/load-more` |
| `SelectionBar` | `count`, `onClear`, `actions` — no `style`: a bar of `IconButton`s | `layout/selection-bar` |
| `RefreshBox` | `refreshing`, `onRefresh`, `style = Style`, `content` | `layout/refresh-box` |

`ResponsiveGrid` is `LazyVerticalGrid` with `GridCells.Adaptive`. `ScrollToTop` sits in a `Box`
over a list and only appears once the reader has left the top. `RefreshBox` is M3's
`PullToRefreshBox`. `SelectionBar` is hidden at count zero.

`LoadMoreButton` and `ScrollToTop` hand their `style` to the button they are made of. `RefreshBox`
takes `style = Style` with no default look (no `StxStyles` entry): the indicator and the drag are
M3's, so `style` dresses the box the gesture is read on. `ResponsiveGrid` is inert and `SelectionBar` hosts several controls (the clear button and the
`actions` slot), so neither takes one.

### Navigation 3 scenes

Package `com.softistx.material.components.navigation`.

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

Package `com.softistx.material.components.surface`.

| Component | Parameters | Story |
| --- | --- | --- |
| `ConfirmDialog` | `visible`, `title`, `text`, `onConfirm`, `onDismiss`, `destructive` | `surfaces/confirm-dialog` |
| `Sheet` | `visible`, `onDismiss`, `content` | `surfaces/sheet` |
| `Drawer` | `open`, `onDismiss`, `gesturesEnabled`, `drawer`, `style = Style`, `content` | `surfaces/drawer` |
| `Tooltip` | `text`, `style = Style`, `content` | `surfaces/tooltip` |
| `HelpTip` | `text`, `description = "More information"`, `style = Style` | `surfaces/help-tip` |
| `HoverCard` | `title`, `text`, `action?`, `onAction?`, `style = Style`, `content` | `surfaces/hover-card` |
| `Menu` | `expanded`, `onDismiss`, `items`, `offset` | `surfaces/menu` |
| `ContextMenu` | `items`, `style = Style`, `content` | `surfaces/context-menu` |
| `Accordion` | `items`, `expanded: Int?`, `onExpandedChange` | `surfaces/accordion` |
| `Disclosure` | `title`, `expanded`, `onExpandedChange`, `content` | `surfaces/disclosure` |
| `Carousel` | `count`, `peek = 48.dp`, `style = Style`, `page` | `surfaces/carousel` |
| `SwipeActions` | `onDismiss`, `background`, `customActions: List<CustomAccessibilityAction> = emptyList()`, `style = Style`, `content` | `surfaces/swipe-actions` |

`HelpTip` is a `Tooltip` around an info `IconButton`, so the sentence and the affordance stay
together. `ConfirmDialog`, `Sheet` and `Drawer` wrap M3. `Toaster` is a stack of M3 `Snackbar`s painted with
`Tone` — M3's host holds one, a dashboard often needs two. `Accordion` is built here: M3 has no accordion.
`Carousel` is M3's multi-browse carousel: one large page, the next one `peek` wide, and each page cut to a rounded
mask that morphs with its size (the shape is `extraLarge`, applied to the content of `page`). `style` dresses the
carousel as a whole (track and dots), never the pages, and has no default look; it is a layout wrapper, so only
static properties and `disabled` apply. The carousel is announced as a collection, each page with its position, and
the dots are hidden from screen readers.
`Disclosure` is one panel; `Accordion` is a list with at most one open. `LabeledProgress` prints
the percentage M3's indicator does not.
`LoadingMark` is M3's morphing `LoadingIndicator` — use it when the wait *is* the content; `contained = true` seats it on a
`primaryContainer` disc (M3's `ContainedLoadingIndicator`) for use over an image or a busy surface;
`Progress` is the spinner attached to a control. `Wavy` / `WavyCircular` are M3's expressive
indicators; `Linear` / `Circular` stay the quiet ones. `TypingIndicator` is three dots that beat
in turn on the effects axis; Material 3 has no typing mark.

`Tooltip`, `HoverCard`, `ContextMenu`, `SwipeActions` and `Drawer` take `style = Style` with no
default look (no `StxStyles` entry); `style` dresses the anchor or the host, and the container M3
paints is not reached. `Drawer` reads as disabled while `gesturesEnabled` is false. `HelpTip` hands
its `style` to its `IconButton`. `Accordion` and `Disclosure` already carry theirs.

On these wrappers the style state has no input behind it: only static properties and `disabled`
apply, and `pressed`, `hovered` and `focused` never fire.

The surfaces that take no `style`, each because the popup is M3's and a probe showed `style` does
not reach it (`alpha(0f)` left the popup fully drawn):

- `ConfirmDialog` wraps `AlertDialog`, which paints its container and scrim in its own window.
- `Sheet` wraps `ModalBottomSheet`, which paints its container and scrim in its own window.
- `Menu` wraps `DropdownMenu`, which paints its container in its own popup.
- `ContextMenu` takes `style` for its anchor only; the menu it opens is `Menu`'s.

`SwipeActions` wraps `SwipeToDismissBox`. A swipe is a gesture a screen reader or a keyboard cannot
make, so `customActions` lists the same actions as named entries of the row's actions menu. The
labels are the caller's, and each action returns `true` once it has handled the request. With none
given the row announces no custom action at all.

```kotlin
import androidx.compose.ui.semantics.CustomAccessibilityAction

SwipeActions(
    onDismiss = { remove(item) },
    background = { Icon(StxIcons.Delete, description = null) },
    customActions = listOf(CustomAccessibilityAction("Delete") { remove(item); true }),
) { ListTile(title = item.name) }
```

## Date and time

Package `com.softistx.material.components.datetime`.

Every interactive part takes `style: Style = Style`, applied last.

| Component | Parameters | Story |
| --- | --- | --- |
| `DateField` | `value: LocalDate?`, `onValueChange`, `label`, `enabled`, `style = Style` | `date-and-time/date-field` |
| `DateRangeField` | `start`, `end`, `onValueChange`, `label`, `enabled`, `style = Style` | `date-and-time/date-range-field` |
| `TimeField` | `value: LocalTime?`, `onValueChange`, `label`, `enabled`, `style = Style` | `date-and-time/time-field` |
| `Calendar` | `value: LocalDate?`, `onValueChange`, `style = Style` | `date-and-time/calendar` |
| `RelativeTime` | `at: Instant`, `now: Instant`, `zone = UTC` | `date-and-time/relative-time` |

The fields are how a form asks; the pickers stay in a dialog. `Calendar` is the same `DatePicker`
inline, for a page that *is* a calendar. Values are `kotlinx.datetime.LocalDate` / `LocalTime`.
`RelativeTime` takes `now` as an argument: a ticking label is the screen's, a snapshot test needs
a frozen instant.

`DateField`, `DateRangeField` and `TimeField` hand their `style` to the `TextField` they are made of
(`StxStyles.field`, empty). `RelativeTime` is not interactive and takes no `style`.

The exceptions, each because Material 3 imposes its own `*Defaults`:

- The three fields add no alpha: M3's text field dims a disabled field itself, so a fade would dim
  it twice.
- `Calendar` takes `style = Style` but has no default look (no `StxStyles` entry): M3's `DatePicker`
  paints and dims its container, days and selection (`DatePickerDefaults`).

## Data

Package `com.softistx.material.components.data`.

| Component | Parameters | Story |
| --- | --- | --- |
| `CommandPalette` | `visible`, `query`, `onQueryChange`, `items`, `onDismiss`, `style = Style` | `data/command-palette` |
| `DataTable` | `columns`, `rows`, `collapseBelow = 600.dp`, `sort: TableSort? = null`, `onSortChange?`, `style = Style`, `headerStyle = Style`, `sortable: (Int) -> Boolean = { true }`, `onRowClick?` | `data/data-table` |
| `Description` | `items: List<DescriptionItem>` | `data/description` |
| `EntityHeader` | `title`, `supporting?`, `leading?`, `actions`, `emphasized = true` | `data/entity-header` |
| `Pagination` | `hasPrevious`, `hasNext`, `onPrevious`, `onNext`, `label?`, `style = Style` | `data/pagination` |
| `Timeline` | `items: List<TimelineItem>` | `data/timeline` |
| `SortControl` | `options`, `selected`, `direction`, `onSelectedChange`, `onDirectionChange`, `label`, `enabled`, `style = Style` | `data/sort-control` |

`DataTable` is a table in expanded panes and a stack of `Description` cards below `collapseBelow`,
measured on the offered width. `Pagination` is previous/next for a keyset page, not `page=3`.
`CommandPalette` filters by a case-insensitive contains; the host opens it (typically ⌘K).
`SortControl` is a `SelectField` plus a direction icon; `cycleSortDirection` is the usual next
value.

`TableSort(column: Int, direction: SortDirection = Asc)` is the sort, `column` an index into `columns`.
The table only reports the click; the caller re-sorts `rows`:

```kotlin
var sort by remember { mutableStateOf<TableSort?>(null) }
DataTable(columns, rows, sort = sort, onSortChange = { sort = it }, sortable = { it != 1 }) { row -> open(row) }
```

With `onSortChange` the headers `sortable` accepts are buttons (ascending first, then flipped); the
others are plain text. `sort` alone is display-only: the arrow and "Ascending" or "Descending" are
shown and announced, with no click. A folded table has no headers, so it sorts through a
`SortControl` beside it. `EntityHeader` announces its title as a heading; `Description` and
`Timeline` titles are item labels, not headings.

The defaults are named in `StxStyles`: `tableRow` (a 0.99 press scale; a row is never disabled, so no
fade) and `sortHeader` (a 0.97 press scale, as a chip has, since a header is compact). `style`
reaches the cards and the table rows, and `headerStyle` every header; only the press look and the
button role wait for `onRowClick` or a sortable header. `Pagination` and `SortControl` hand their `style` to the buttons and field they
are made of.

The parts that take no `style` are not interactive: `Description`, `Timeline`, `EntityHeader`
(`leading` and `actions` carry their own).

The exceptions, each because Material 3 imposes its own `*Defaults`:

- `CommandPalette` takes `style = Style` but has no default look (no `StxStyles` entry), and it
  dresses the list of commands, not the dialog: M3's `AlertDialog` paints its own container over its
  modifier, and each command is a `ListTile` with the list-tile style.
- The collapsed cards of `DataTable` are `Card`s: they keep `StxStyles.card`, and `style` is
  added to it.

## Media

Package `com.softistx.material.components.media`.

| Component | Parameters | Story |
| --- | --- | --- |
| `Avatar` | `name`, `image?`, `tone?`, `size = 40.dp` | `media/avatar` |
| `AvatarGroup` | `items: List<AvatarItem>`, `max = 4`, `size = 32.dp` | `media/avatar-group` |
| `PersonCard` | `name`, `supporting?`, `image?`, `tone?`, `onClick?`, `style = Style`, `action?` | `media/person-card` |
| `SeenBy` | `items`, `max = 3`, `label?` | `media/seen-by` |
| `StxImage` | `model`, `description` | — |
| `Gallery` | `images`, `onSelect?`, `minSize = 160.dp`, `style = Style` | — |
| `Lightbox` | `visible`, `model`, `onDismiss`, `onPrevious?`, `onNext?` | `media/lightbox` |
| `VideoSurface` / `PdfSurface` / `CameraSurface` | `content` slot, `overlay`, `ratio` | `media/video-surface` |

`Avatar` shows initials when there is no image, and an optional `Tone` ring. `AvatarGroup` stacks
them with a `+N` overflow circle — not initials of `"+12"`, which would read `+1`. `PersonCard` is
the compact identity that sits in a grid, a mention, a search hit: a `ListTile` is a row in a list,
`EntityHeader` is the top of a page. `SeenBy` is an `AvatarGroup` plus `seenByLabel` — "Sent" when
nobody has looked, "Seen by N" otherwise. `StxImage` is Coil
with this library's `Skeleton` / `EmptyState`. Video, PDF and camera are **chrome**: the host fills
the slot with a renderer, so `Button` never pays for Media3, PdfRenderer or CameraX.

With `onSelect` a `Gallery` tile is a button that presses to 0.99 (`StxStyles.tile`); without it
`style` has nothing to dress. `PersonCard` hands its `style` to its `Card`, which keeps
`StxStyles.card`. `Avatar`, `AvatarGroup`, `SeenBy`, `StxImage` and the media surfaces are not press targets and
take no `style`; `Lightbox` hosts three controls (close, previous, next), so it takes none.

## Motion helpers

Package `com.softistx.material.foundation.motion`.

| Helper | What it does | Story |
| --- | --- | --- |
| `Modifier.shimmer()` | The sweeping highlight `Skeleton` is built from | `feedback/skeleton` |
| `Modifier.animateStagger(index)` | Rises and fades a list item in, offset by its position | `screens/orders-screen` |

`animateStagger` counts its wait in frames of the composition's clock, not in wall-clock time, so a
headless render steps through it. It runs **two** animations, not one: the rise is spatial and the
fade is effects. A spatial spring overshoots by design, and an alpha past 1 is at best clamped — the
two axes are not interchangeable. `motion/spatial-and-effects` shows the difference.

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
