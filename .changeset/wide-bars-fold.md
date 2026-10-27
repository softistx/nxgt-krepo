---
"stx-material": minor
---

Flexible app bars, a morphing Stepper marker and a contained `LoadingMark`.

- `AppBar(…, scrollBehavior: TopAppBarScrollBehavior? = null)`, placed before the trailing `actions` lambda so existing calls compile. Given `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`, a `Medium` or `Large` bar collapses into the small bar.
- `LoadingMark(…, contained = false)`: `true` seats the mark on a `primaryContainer` disc (M3's `ContainedLoadingIndicator`), determinate or not.
- Visible default changes: `AppBarSize.Medium` and `Large` are M3's flexible bars (`MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`), so their expanded title is `headlineMedium` / `displaySmall` where it was `titleLarge`, and it wraps instead of staying on one line. The expanded height is 112 dp before and after for `Medium`, and 152 dp before, 120 dp after for `Large`. The title of every bar is emphasised and announced as a heading: `Small` and `Centered` use `titleLarge` emphasised, on one line (was `titleLarge`, plain, unbounded). The `Stepper` marker is a 32 dp plate (was a 28 dp rounded square): a circle, or a `Cookie9Sided` for the current step, morphing on `motion.spatial` when the step advances.
