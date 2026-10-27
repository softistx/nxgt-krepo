---
"stx-material": minor
---

`Button` and `ButtonSurface` morph their shape on press (a pill at rest, squarer while pressed, through M3's `shapes =` overload) and no longer scale. New `size: ButtonSize` (`ExtraSmall` to `ExtraLarge`, default `Small`, the former 40 dp), also on `BusyButton`, `ConfirmButton` and `ResponsiveButton`. New `StxStyles.morphingButton`. Visible default change: the press gives with its corners instead of a 0.97 scale. A Link button keeps its 40 dp height at every size instead of growing with `size`.
