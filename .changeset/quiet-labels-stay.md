---
"stx-material": minor
---

The compact `NavigationSuite` bar keeps every label on one line, `StxIcons` gains four icons, and `animateStagger` is driven by the frame clock (#295). New API: `StxIcons.Bell`, `StxIcons.Printer`, `StxIcons.Phone` and `StxIcons.EditNote` (24 dp vectors in the style of the others). No signature changes. Visible default changes:

- `NavigationSuite` on a compact window: a destination label is one line that ends in an ellipsis when it does not fit, with 4 dp of padding on each side so neighbouring labels never touch (before: a long label such as "Dashboard" or "Timetable" wrapped onto a second line or ran into its neighbour). Medium and wide layouts are unchanged.
- `Modifier.animateStagger`: the wait before an item arrives is counted in frames of the composition's clock (before: a wall-clock `delay`). The timing on screen is the same; a headless `ImageComposeScene` render or a test that steps the clock now sees the item arrive instead of leaving it invisible. Reduced motion still shows the item at once.
