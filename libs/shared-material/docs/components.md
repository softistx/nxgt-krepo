# Components

Every component the library ships, what it takes, and where to see it. **This is the file a new
component is documented in**, in the change that adds it.

Open the catalogue with `./kotlin run -m desktop`; the story id is the line under each story's
title. Every component takes `modifier: Modifier`, and every *interactive* one takes
`style: Style = Style` — both are left out of the tables below because they are on everything that
has them.

**Most of these are Material 3's components, dressed.** `Button` is M3's `Button`, `Chip` is its
`FilterChip`, `Card`, `ListTile` and `StatusBadge` are its `Card`, `ListItem` and `Badge`. What is
added is the default that was missing: the colour matrix resolved into M3's own `*Colors`, the
padding and rhythm inside a card, a hover state M3's chip does not have, and a press that gives
under the finger. Only `Alert`, `EmptyState`, `Skeleton` and `ResponsiveButton` are built from
primitives, because M3 has nothing to start from. AGENTS.md's *Building a component* is the rule.

## Foundation

| Story | Shows |
| --- | --- |
| `foundation/typography` | The 20 `TypographyVariant` roles at each `Emphasis` |
| `foundation/spacing` | The eight-step spacing scale, to scale |
| `foundation/radii` | Every radius derived from one `base` |
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

`StrangeIcons` holds ten hand-built vectors: `Add`, `Check`, `Close`, `ChevronRight`, `Delete`,
`Edit`, `Inbox`, `Person`, `Search`, `Warning`. They are defined in code because **no icon pack is
reachable from here**: the Kotlin Toolchain's `$compose` catalog has no key for the Material icons,
`$compose.material` does not carry `material-icons-core` in Compose Multiplatform 1.11, and the
AndroidX icon artifacts are Android-only. An application that wants a thousand glyphs should depend
on a pack directly and pass the `ImageVector` in — every component here takes one.

## Buttons

| Component | Parameters | Story |
| --- | --- | --- |
| `Button` | `text`, `onClick`, `variant = Filled`, `color = Primary`, `enabled` | `buttons/button` |
| `ButtonSurface` | as `Button`, plus a `RowScope` `content` slot instead of `text` | `buttons/button` |
| `IconButton` | `icon`, `description`, `onClick`, `variant = Ghost`, `color = Neutral`, `size`, `enabled` | `buttons/icon-button` |
| `ResponsiveButton` | `text`, `icon`, `onClick`, `variant`, `color`, `enabled`, `collapseBelow = 360.dp` | `buttons/responsive-button` |
| `ButtonRow` | `align = End`, `content: RowScope` | `buttons/button-row` |

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

`CardVariant` — `Filled`, `Outlined`, `Elevated`.

A `Card` is interactive **iff** `onClick` is not null: it grows a hover and a press state only when
it does something. `ListTile` follows the same rule.

`Chip` is M3's `FilterChip`, so selection, the tick mark, the border and the shape morph are all
M3's and animate on their own. The hover is not: `SelectableChipColors` has thirteen colours and no
hover among them — M3's own source carries the `TODO(…): Support other states: hover, focus, drag` —
so `chipColors(hovered)` adds the state layer M3 would have, composited over whichever container the
chip is already wearing and faded on the effects axis. A chip with no `onClick` does not light up,
because it does nothing.

`Alert` owns its own enter and exit — pass `visible` and it animates itself; there is no
`AnimatedVisibility` for the caller to write. `EmptyState` fades in for the same reason, and
`Skeleton` shimmers on its own.

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

`screens/orders-screen` is the acceptance criterion, not a demonstration. It is a header, an alert,
a filter row, and a list that switches between loading, empty and loaded — written with no
`animate*AsState`, no transition, no interaction source, and no remembered hover, press or
visibility. Its one `remember` is the selected filter, which is business state a real application
would own too.

If a future phase makes that file need plumbing, the phase is not finished.
