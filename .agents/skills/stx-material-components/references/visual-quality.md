# What makes a component look modern here

The eight points of the quality bar in `SKILL.md`, with the library's own means for each. Judge a component by rendering it (catalogue story, light and dark), not by reading its code.

## 1. Hierarchy from the type scale

One line dominates; everything else steps back. Size is not the only lever: the same size at `Emphasis.Medium` (0.72) or `Subtle` (0.5) is how secondary text stays in the palette instead of picking a grey. `TypographyVariant` has the 15 M3 roles plus `Overline`, `Caption`, `Metric` (a figure read at a glance), `Code` and `Link`. An Expressive screen spends the `*Emphasized` weight once per region — the hero title, the headline figure — and leaves body regular. Two emphasised lines side by side cancel each other.

Tell: if you cannot say which line the eye lands on first, the hierarchy is missing.

## 2. One accent

`colorScheme.primary` is the accent and appears on the one action that matters per region. Meaning (success, warning, info, error) comes from `Tone` and `StxTheme.colors.tone(tone)` (`main`, `onMain`, `container`, `onContainer`), never from a literal. Chips, badges and alerts use the container pair so a tone reads the same at any size. Do not add a second brand colour to a component; the secondary and tertiary roles exist for selection and for the rare contrast.

## 3. Tonal surfaces over borders

Depth is the `surfaceContainer*` ladder (`surfaceContainerLowest` … `surfaceContainerHighest`): a card on the page is `surfaceContainerLow`, a field inside it `surfaceContainerHigh`. Selection is a tone change (`secondaryContainer`), not a border. A hairline is for the one case where two same-tone surfaces meet; the shadow (`CardDefaults.elevatedCardElevation()`) is for a thing that genuinely floats.

## 4. Shape and rhythm

Rounder than M3 baseline: `largeIncreased` for cards and containers, `extraLargeIncreased` for sheets, `CircleShape` for pills. Nested shapes step down one slot, so an inner corner never looks rounder than its container. Spacing comes only from `StxTheme.spacing` (`xxs` 2 … `xxl` 48): `md` (16) inside a card, `lg` (24) between sections, `xxl` around an empty state so it reads as deliberate. Equal gaps look accidental; related things sit closer than unrelated ones.

## 5. Targets and roles

Every control is at least 48 dp tall and wide (`minimumInteractiveComponentSize()`, already in M3 controls), and says what it is (`Role`). A dense desktop layout may shrink the visual, never the target. `compose-accessibility` has the semantics.

## 6. Motion with meaning

Motion answers one of three questions: what did my touch do (press give, shape morph), where did that come from (a card rising, a menu popping from its anchor), what changed (a number sliding, a list cascading). Anything else is decoration. Spatial for movement, effects for colour and alpha, both from `StxMotion`. `compose-motion` has the recipes and the list of what not to animate.

## 7. The states around the happy path

A component that shows data also has: **empty** (`EmptyState` with a way out in its `action` slot), **loading** (`Skeleton` for content whose shape is known, `LoadingMark` or `LoadingIndicator` for a wait of unknown length), **error** (`Alert` with a tone, or a field's `error`, with the fix in words) and **disabled** (alpha 0.38 via `DISABLED_ALPHA`, never a different hue). `UiState<T>` (`Loading`, `Data`, `Error`) is the carrier. A story shows each of them.

## 8. Dark, large type and no motion

- **Dark:** only scheme roles, so dark is free; check any `Color(...)` you wrote, and that images and illustrations have a dark treatment.
- **Large type:** at 2× font scale text wraps and containers grow; fixed `height` on text containers clips. Use `heightIn(min = …)`, not `height(…)`.
- **No motion:** `StxMotion(enabled = false)` snaps every spec; the component must look finished in its end state.

## Signs of a flat or templated screen

Everything at one weight; three accents; a border around every card; identical gaps everywhere; a spinner for every wait; a list that appears all at once, or a bounce on a control that should be calm; text on a tinted container in the wrong `on` colour.
