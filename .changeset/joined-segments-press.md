---
"stx-material": minor
---

`SegmentedControl` and `ViewToggle` are Material 3's connected button group: toggle buttons that touch, round at the two ends, squarer at the seams and morphing under the press, the chosen one filled with no checkmark and still announced as a `RadioButton` (now also `selected`). `SegmentedControl` gains `icons: List<ImageVector>? = null`, `ViewToggle` gains `style: Style = Style`. Visible default changes: segments share one width (or span the width a caller fills), the old outlined `SegmentedButton` look is gone, and `ViewToggle` is one joined pair instead of two separate round icon toggles. Also fixed: the press give of `Tabs` and `SegmentedControl` never ran, because the style's interaction source was not the one any tab or segment used; each tab now has its own, so a pressed tab gives 0.97.
