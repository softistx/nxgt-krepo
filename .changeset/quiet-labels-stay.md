---
"stx-material": minor
---

The compact `NavigationSuite` bar keeps every label on one line, `StxIcons` gains four icons, and `animateStagger` is driven by the frame clock (#295). New API: `StxIcons.Bell`, `StxIcons.Printer`, `StxIcons.Phone` and `StxIcons.EditNote` (24 dp vectors in the style of the others). No signature changes. Visible default changes:

- `NavigationSuite` on a compact window: a destination label is one line that ends in an ellipsis when it does not fit (before: a long label wrapped onto a second line). A compact bar holds three to five destinations (M3's rule); past that, labels truncate, so move the extra ones behind a "More" entry, a drawer or the rail. Medium and wide layouts are unchanged.
- `Modifier.animateStagger`: the wait before an item arrives is counted in frames of the composition's clock (before: a wall-clock `delay`). The timing on screen is the same, within a frame; a headless `ImageComposeScene` render, which steps frames without waiting, now sees the item arrive instead of leaving it invisible. Reduced motion still shows the item at once.
