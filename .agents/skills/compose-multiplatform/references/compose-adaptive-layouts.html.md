<!-- Generated from https://kotlinlang.org/docs/multiplatform/compose-adaptive-layouts.html (v) on 2026-08-28. Do not edit; re-run fetch_docs.py. -->

# Adaptive layouts

To provide a consistent user experience on all types of devices, adapt your app's UI to different display sizes, orientations, and input modes.

## Designing adaptive layouts

Follow these key guidelines when designing adaptive layouts:

- Prefer [canonical layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts) patterns like list-detail, feed, and supporting pane.
- Maintain consistency by reusing shared styles for padding, typography, and other design elements. Keep navigation patterns consistent across devices while following platform-specific guidelines.
- Break complex layouts into reusable composables for flexibility and modularity.
- Adjust for screen density and orientation.

## Using window size classes

Window size classes are predefined thresholds, also referred to as breakpoints, that categorize different screen sizes to help you design, develop, and test adaptive layouts.

Window size classes categorize the display area available to your app into three categories for both width and height: compact, medium, and expanded. As you make layout changes, test the layout behavior across all window sizes, especially at the different breakpoint thresholds.

To use `WindowSizeClass` classes, add the `material3.adaptive` dependency to the common source set in your module's `build.gradle.kts` file:

```kotlin
commonMain.dependencies {
    implementation("org.jetbrains.compose.material3.adaptive:adaptive:1.3.0-beta02")
}
```

The `WindowSizeClass` API allows you to change your app's layout based on the available display space. For example, you can manage visibility of the top app bar depending on the window height:

```kotlin
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
...

@Composable
fun MyApp(
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
) {
    // Determines whether the top app bar should be displayed
    val showTopAppBar = windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    // Uses bar visibility to define UI
    MyScreen(
        showTopAppBar = showTopAppBar,
        /* ... */
    )
}
```

## What's next

Learn more about adaptive layouts in the [Jetpack Compose documentation](https://developer.android.com/develop/ui/compose/layouts/adaptive).

21 August 2026

Working with modifiers

Compose UI previews