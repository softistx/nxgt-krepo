---
name: compose-accessibility
description: Accessibility for Compose Multiplatform UI on Android, iOS and desktop — semantics, roles, 48 dp targets, contrast, font scale, keyboard focus, reduced motion — and how to test them. Use when writing or reviewing any interactive composable.
---

# Accessibility in Compose Multiplatform

Every assistive tool reads one thing, the **semantics tree**: TalkBack, VoiceOver, macOS and Windows screen readers, XCTest and `runComposeUiTest`. Material 3 fills most of it; this skill covers what you add and what you must check. `references/INDEX.md` lists the fetched pages: Android's Compose pages are the authority on APIs, JetBrains' on iOS and desktop.

## Semantics you set

Properties (`role`, `stateDescription`, `heading`, `liveRegion`, …) import from `androidx.compose.ui.semantics`; `clickable` from `androidx.compose.foundation`; `toggleable`, `selectable` and `selectableGroup` from `androidx.compose.foundation.selection`; `minimumInteractiveComponentSize` from `androidx.compose.material3`.

| Need | Write |
| --- | --- |
| A name for a non-text control | `Icon`'s `description` (required in stx-material; `null` only when a label sits beside it), or `Modifier.semantics { contentDescription = "…" }` |
| What it is | `clickable(role = Role.Button)`, `toggleable(role = Role.Switch)`, `selectable(role = Role.RadioButton)`, or `semantics { role = … }`. Roles: `Button Checkbox Switch RadioButton Tab Image DropdownList ValuePicker Carousel` |
| Its state | `stateDescription = "Notifications on"`, `selected = …`, `toggleableState`, `disabled()`, `error("Enter an email")` |
| A section title | `semantics { heading() }` |
| A change to announce | `liveRegion = LiveRegionMode.Polite` (or `Assertive`) on a count, status or toast; `paneTitle` for a pane or dialog |
| A row read as one stop | `semantics(mergeDescendants = true) { }` — `clickable` and `toggleable` already merge |
| Replace a subtree's reading | `clearAndSetSemantics { contentDescription = "Order 12, paid" }` |
| Decoration | `hideFromAccessibility()` (`invisibleToUser()` is deprecated) |
| A radio set | `Modifier.selectableGroup()` on the container |
| An action that is a gesture | `customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true })`; a click label with `clickable(onClickLabel = "Open order")` |
| Reading order | default is top to bottom, start to end; `isTraversalGroup = true` plus `traversalIndex = -1f` reads a group earlier (default `0f`, lower first) |

Rules that bite:

- **M3 does not set every role.** A clickable `Card` has none (measured): the component adds `Role.Button`, and its spec asserts it. `ToggleButton` announces `Role.Checkbox`; in a single-choice group pass `Modifier.semantics { role = Role.RadioButton }` — a role passed in `modifier` wins over the one inside the M3 component (measured).
- Merge before you describe: put `contentDescription` on the merged parent, not on each child, or the reader says it twice.
- A swipe, drag or long-press that is the only way to do something needs a `customActions` entry.
- State lives in semantics, not in colour: a selected tile says `selected`; a tone also gets an icon or a word.

```kotlin
Row(
    Modifier
        .minimumInteractiveComponentSize()
        .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
        .semantics { stateDescription = if (on) "Notifications on" else "Notifications off" },
) { Text("Notify me") }
```

## Targets, contrast, type

- **48 dp.** M3 controls already apply `minimumInteractiveComponentSize()`. A custom clickable applies it before any size modifier; a 24 dp icon stays 24 dp to the eye and 48 dp to the finger. `LocalMinimumInteractiveComponentSize` is the 48 dp default.
- **Contrast (WCAG AA).** 4.5:1 for normal text, 3:1 for large text and for the edges of controls. Pair every container with its own `on*` role; the default seed passes AA on all eight pairs checked (`primary`, `surface`, `secondaryContainer`, `surfaceContainer`, `success`, `warning`, `info`, `error`) in light and dark. Measured on the default theme (`EmphasisContrastTest`): `Emphasis.Subtle` (0.72) is 4.8-5.2:1 in light and 7.4-8.2:1 in dark on `surface`, `surfaceContainer` and `surfaceContainerHigh`, so it passes AA for body text; `Emphasis.Medium` (0.80) is 6.0-6.6:1 in light and 8.8-9.9:1 in dark; `outline` is 4.3–5.8:1 and fine for a control edge; `outlineVariant` is 1.6–2.0:1 and decorative only.
- **High contrast.** material-kolor takes `contrastLevel` (`Contrast.Medium` 0.5, `High` 1.0) in `dynamicColorScheme`. Neither M3 nor Compose reads the OS setting in common code; iOS exposes it as `UIAccessibilityDarkerSystemColorsEnabled`, so an app passes the choice in (`references/compose-ios-accessibility.md`).
- **Font scale.** Text is `sp`. No fixed `height` on a container that holds text; use `heightIn(min = …)`. A row that fits at 1× wraps or scrolls at 2×.

## Keyboard, focus and pointer

Desktop and iPad users arrive by keyboard. `clickable`, `toggleable` and every M3 control are focusable and show a focus layer; a custom one adds `Modifier.focusable()`. Tab order is composition order; `FocusRequester` with `Modifier.focusRequester(..)` moves focus on purpose (a dialog's first field). Key handling is `onPreviewKeyEvent { it.type == KeyEventType.KeyUp && it.key == Key.Enter }`. M3 controls draw their own focus and hover layers. Add a `focused { }`/`hovered { }` change in a Style only on a component built from primitives, or where a render spec shows the M3 layer is invisible (`ChipHoverTest`). See `references/touch-input-focus.md` and `references/compose-desktop-keyboard.md`.

## Reduced motion

`compose-motion` owns it (both settings, and where the OS preference comes from on each platform).

## Platform differences

| | Status |
| --- | --- |
| Android | TalkBack, Switch Access; Compose accessibility checks in tests (`references/accessibility-testing.md`) |
| iOS | VoiceOver, AssistiveTouch, Full Keyboard Access; semantics map to `UIAccessibility`, `testTag` to `accessibilityIdentifier`; `performAccessibilityAudit()` in XCUITest; no high-contrast `ColorScheme` |
| macOS | Fully supported; Accessibility Inspector |
| Windows | Through Java Access Bridge: `jabswitch.exe /enable` while developing, `modules("jdk.accessibility")` in the distribution |
| Linux | Not supported |

## Test it here

Specs live in `libs/ui/stx-material/test@jvm/`; `ClickableRolesTest` is the model, and `stx-material-components/references/worked-example.md` a complete one (`ChoiceTileSemanticsTest`). Import `androidx.compose.ui.test.v2.runComposeUiTest` (v1 is deprecated).

```kotlin
runComposeUiTest {
    setContent { StxTheme(isDark = false) { ListTile("Pro", onClick = {}) } }
    onNodeWithText("Pro")
        .assertHasClickAction()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        .assertHeightIsAtLeast(48.dp)
}
```

- `onNodeWithContentDescription("…")`, `assertContentDescriptionEquals`, `assertIsOn`/`assertIsOff`, `assertTouchHeightIsEqualTo(48.dp)`; `onRoot(useUnmergedTree = true).printToLog("TREE")` shows what merging hid.
- Contrast: `com.materialkolor.ktx.hasEnoughContrast(other, ContrastThreshold.WCAG_AA_NORMAL_TEXT)` on `colorScheme` pairs, in both modes; composite an `Emphasis` alpha with `compositeOver(surface)` first.
- Font scale: `CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) { … }`, then assert the node is still displayed and at least 48 dp.
- Reduced motion: see `compose-motion`.

## Gaps in this library when this was written (2026-10-26)

Re-check with `grep -rn 'heading()\|customActions' libs/ui/stx-material/src` before relying on them:

- No component sets `heading()` (`SectionHeader`, `Dialog` titles, `FormSection`).
- `SwipeActions` is gesture-only; it has no `customActions`.
- `Card(onClick = …)` sets no `Role.Button`.
- Fixed in #297: `StxTheme` reads the OS reduce-motion setting when given neither `motionScheme` nor `motion` (`systemReducesMotion()`); `Emphasis.Subtle` holds 4.5:1. Still open: no read of the OS increase-contrast setting (follow-up in the roadmap).
