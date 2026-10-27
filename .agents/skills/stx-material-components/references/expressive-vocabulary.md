# M3 Expressive vocabulary, as it exists in 1.12.0-alpha03

Everything here was compiled against the resolved jar (`material3-desktop-1.12.0-alpha03`, androidx 1.5.0-alpha22 on Android), not copied from the web. Re-check the version in `material3-compose/references/INDEX.md` before trusting a name after a bump.

Gates. The module opts in to `ExperimentalMaterial3ExpressiveApi` and `ExperimentalMaterial3Api` in `module.yaml`, so none of this needs an `@OptIn`. An API annotated plain `Material3ExpressiveApi` (no `Experimental`) is `@RestrictTo`, library-internal: `IconButtonDefaults.*ContainerSize()` and the `shapes()` of chip defaults are in that group. Leave them.

## Shape morph on press

M3 buttons morph from `shape` to `pressedShape` (toggles also to `checkedShape`). `Button` drives the morph with the scheme's default *effects* spec (its source says "to prevent any bounce"); `ToggleButton` uses the fast *spatial* spec. The `shapes =` overload replaces `shape =`; the two do not combine.

```kotlin
val height = ButtonDefaults.MediumContainerHeight            // 56 dp
MaterialButton(
    onClick = onClick,
    shapes = ButtonDefaults.shapes(),                          // or shapes(shape, pressedShape)
    modifier = Modifier.heightIn(min = height),
    contentPadding = ButtonDefaults.contentPaddingFor(height),
) { Text("Save", style = ButtonDefaults.textStyleFor(height)) }
```

| Need | API |
| --- | --- |
| Morphing buttons | `Button`, `ElevatedButton`, `FilledTonalButton`, `OutlinedButton`, `TextButton` each have an `(onClick, shapes: ButtonShapes, …)` overload |
| Defaults | `ButtonDefaults.shapes()`, `shapes(shape, pressedShape)`, `shapesFor(buttonHeight)`, `squareShape`, `pressedShape` |
| Sizes | `ExtraSmallContainerHeight` 32, `MinHeight` 40 (small; 36 when a precision pointer such as a desktop mouse is detected), `MediumContainerHeight` 56, `LargeContainerHeight` 96, `ExtraLargeContainerHeight` 136 dp |
| Per size | `contentPaddingFor(height)`, `iconSizeFor(height)`, `iconSpacingFor(height)`, `textStyleFor(height)`, `shapesFor(height)` |
| Icon buttons | `IconButton(onClick, shapes: IconButtonShapes, …)`, `FilledIconButton(…, shapes)`, `IconToggleButton(checked, onCheckedChange, shapes: IconToggleButtonShapes, …)`; `IconButtonDefaults.shapes()` |
| Toggle buttons | `ToggleButton`, `TonalToggleButton`, `ElevatedToggleButton`, `OutlinedToggleButton`; `ToggleButtonDefaults.shapes()`, `shapesFor(height)` |
| Split button | `SplitButtonLayout(leadingButton, trailingButton, modifier, spacing)` with `SplitButtonDefaults.LeadingButton(onClick) { }` and `TrailingButton(onClick) { }` |
| FAB menu | `FloatingActionButtonMenu(expanded, button = { ToggleFloatingActionButton(checked, onCheckedChange) { … } }) { FloatingActionButtonMenuItem(onClick, text, icon) }` |

Where a button morphs, its Style carries no press `scale`: two give-under-the-finger effects on one control fight. Everything that does not morph (cards, rows, chips) keeps `scale`.

## Connected button group, for choosing one of a few

There is no `ConnectedButtonGroup` composable in this version. A connected group is a `Row` spaced by `ButtonGroupDefaults.ConnectedSpaceBetween` of `ToggleButton`s that take the leading, middle and trailing shapes:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
    labels.forEachIndexed { index, label ->
        val shapes =
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        ToggleButton(
            checked = index == selected,
            onCheckedChange = { if (it) onSelect(index) },
            shapes = shapes,
        ) { Text(label) }
    }
}
```

`ToggleButton` announces itself as `Role.Checkbox`. For a single choice pass `Modifier.semantics { role = Role.RadioButton }` (the modifier you pass wins; measured) and wrap the row in `Modifier.selectableGroup()`. See `compose-accessibility`.

The standard (unconnected) `ButtonGroup(overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) }) { clickableItem(onClick, label, icon?); toggleableItem(checked, label, onCheckedChange, icon?); customItem(buttonGroupContent, menuContent) }` expands the pressed child and overflows into a menu. `SingleChoiceSegmentedButtonRow` and `SegmentedButton` still exist; the owner chose the connected group for segments.

## Shapes

`MaterialTheme.shapes` has eight slots (`material3-compose/references/shapes.md`): `extraSmall` 4, `small` 8, `medium` 12, `large` 16, `largeIncreased` 20, `extraLarge` 28, `extraLargeIncreased` 32, `extraExtraLarge` 48 dp. Inside a `Style`, `shapes.<slot>` reads the same instance. Pills are `CircleShape`.

| Use | Slot |
| --- | --- |
| Chips, small fields, tooltips | `small` / `medium` |
| Cards, list containers, alerts | `largeIncreased` |
| Sheets, dialogs, hero blocks | `extraLargeIncreased` |
| A very soft feature container | `extraExtraLarge` |

Decorative masks come from `MaterialShapes`, a set of `RoundedPolygon`s: `Circle Square Slanted Arch Fan Arrow SemiCircle Oval Pill Triangle Diamond ClamShell Pentagon Gem Sunny VerySunny Cookie4Sided Cookie6Sided Cookie7Sided Cookie9Sided Cookie12Sided Ghostish Clover4Leaf Clover8Leaf Burst SoftBurst Boom SoftBoom Flower Puffy PuffyDiamond PixelCircle PixelTriangle Bun Heart`.

```kotlin
Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()))
```

`RoundedPolygon.toShape(startAngle = 0)` and `toPath()` are `@Composable` and remembered. Use them for an avatar frame, a step marker or an empty-state badge, never for a control that has to feel like a button.

## Type

`MaterialTheme.typography` has 15 `*Emphasized` styles beside the 15 regular ones (`displayLargeEmphasized` … `labelSmallEmphasized`). Emphasised is heavier at the same size. Spend it on one line per screen region. In this library a component asks `Typography(text, variant, emphasized = …)` where the parameter exists (read `foundation/text/Typography.kt`) and never reads `MaterialTheme.typography` itself.

## Lists

`ListItem(onClick, shapes = ListItemDefaults.shapes()) { … }` is an interactive row that morphs on press, select, focus and hover. A run of rows that read as one group:

```kotlin
items.forEachIndexed { index, item ->
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
        colors = ListItemDefaults.segmentedColors(),
    ) { Text(item) }
}
```

Also `ListItem(selected, onClick, …)`, `ListItem(checked, onCheckedChange, …)` and `ListItemDefaults.SegmentedGap`.

## Bars, toolbars, waits

| Need | API |
| --- | --- |
| Collapsing title, medium or large | `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar(title, modifier, subtitle, navigationIcon, actions, titleHorizontalAlignment, collapsedHeight, expandedHeight, windowInsets, colors, scrollBehavior)` |
| Scroll behaviour | `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`, `pinnedScrollBehavior()`, `enterAlwaysScrollBehavior()` |
| Bottom bar | `FlexibleBottomAppBar(modifier, containerColor, contentColor, contentPadding, horizontalArrangement, expandedHeight, windowInsets, scrollBehavior) { }` |
| Search in a bar | `AppBarWithSearch` |
| Floating toolbar | `HorizontalFloatingToolbar(expanded, …) { }`, and a variant taking `floatingActionButton` |
| Navigation | `ShortNavigationBar` + `ShortNavigationBarItem`, `WideNavigationRail` + `WideNavigationRailItem`, `ModalWideNavigationRail` |
| Indeterminate wait | `LoadingIndicator()`, `LoadingIndicator(progress = { 0.4f })`, `ContainedLoadingIndicator()` |
| Wavy progress | `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator` exist; the owner kept `Progress` linear |
| Carousel | `HorizontalMultiBrowseCarousel(state = rememberCarouselState { n }, preferredItemWidth, …) { index -> }`, `HorizontalCenteredHeroCarousel`. `HorizontalUncontainedCarousel` is not in this version |

Use `LoadingIndicator` when the length of the wait is unknown and a bar would lie; use `Progress` when a fraction is known.

## Motion and theme

- `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography)` is what `StxTheme` installs. `MotionScheme.expressive()` springs: default spatial damping 0.8 stiffness 380, fast spatial 0.6 / 800, slow spatial 0.8 / 200; every effects spec is damping 1.0. `MotionScheme.standard()` damps at 0.9 and is stiffer. `compose-motion` has the rest.
- Palette. material-kolor 5.0.1: `dynamicColorScheme(seedColor, isDark, style = PaletteStyle.TonalSpot, contrastLevel = …, specVersion = ColorSpec.SpecVersion.SPEC_2025)`. Styles: `TonalSpot Neutral Vibrant Expressive Rainbow FruitSalad Monochrome Fidelity Content`; `Contrast` is `Default 0.0`, `Medium 0.5`, `High 1.0`, `Reduced -1.0`. The owner's default is `TonalSpot` + `SPEC_2025`; `stxColorScheme` and `StxThemeProvider` are the seam, so change the palette there and not in a component.

## Not in this version

`HorizontalUncontainedCarousel`; a `ConnectedButtonGroup` composable; a `MotionScheme` with no animation (hence `StxMotion(enabled = false)`).
