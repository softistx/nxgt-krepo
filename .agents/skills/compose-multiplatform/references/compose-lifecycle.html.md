<!-- Generated from https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html (v) on 2026-08-28. Do not edit; re-run fetch_docs.py. -->

# Lifecycle

Lifecycle of components in Compose Multiplatform is adopted from the Jetpack Compose [lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle) concept. Lifecycle-aware components can react to changes in the lifecycle state of other components and help you produce better-organized, and often lighter, code that is easier to maintain.

Compose Multiplatform provides a common `LifecycleOwner` implementation, which extends the original Jetpack Compose functionality to other platforms and helps observe lifecycle states in common code.

To use the multiplatform `Lifecycle` implementation, add the following dependency to your `commonMain` source set:

```kotlin
kotlin {
    // ...
    sourceSets {
        // ...
        commonMain.dependencies {
            // ...
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
        }
        // ...
    }
}
```

You can track changes to the multiplatform Lifecycle implementation in our [What's new](https://kotlinlang.org/docs/multiplatform/whats-new-compose.html) or follow specific EAP releases in the [Compose Multiplatform changelog](https://github.com/JetBrains/compose-multiplatform/blob/master/CHANGELOG.md).

## States and events

The flow of lifecycle states and events (same as for the [Jetpack lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle)):

## Lifecycle implementation

Composables usually don't need unique lifecycles: a common `LifecycleOwner` provides a lifecycle for all interconnected entities. By default, all composables created by Compose Multiplatform share the same lifecycle – they can subscribe to its events, refer to the lifecycle state, and so on.

The `LifecycleOwner` object is provided as a [CompositionLocal](https://developer.android.com/reference/kotlin/androidx/compose/runtime/CompositionLocal). If you would like to manage a lifecycle separately for a particular composable subtree, you can [create your own](https://developer.android.com/topic/libraries/architecture/lifecycle#implementing-lco) `LifecycleOwner` implementation.

When working with coroutines in multiplatform lifecycles, remember that the `Lifecycle.coroutineScope` value is tied to the `Dispatchers.Main.immediate` value, which might be unavailable on desktop targets by default. To make coroutines and flows in lifecycles work correctly with Compose Multiplatform, add the `kotlinx-coroutines-swing` dependency to your project. See [`Dispatchers.Main` documentation](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-main.html) for details.

- Learn how the lifecycle works in navigation components in [Navigation and routing](compose-navigation-routing.html).
- Learn more about the multiplatform ViewModel implementation on the [Multiplatform ViewModel](compose-viewmodel.html) page.

## Mapping Android lifecycle to other platforms

### iOS

| Native events and notifications | Lifecycle event | Lifecycle state change |
| --- | --- | --- |
| `viewDidDisappear` | `ON\_STOP` | `STARTED` → `CREATED` |
| `viewWillAppear` | `ON\_START` | `CREATED` → `STARTED` |
| `willResignActive` | `ON\_PAUSE` | `RESUMED` → `STARTED` |
| `didBecomeActive` | `ON\_RESUME` | `STARTED` → `RESUMED` |
| `didEnterBackground` | `ON\_STOP` | `STARTED` → `CREATED` |
| `willEnterForeground` | `ON\_START` | `CREATED` → `STARTED` |
| `viewControllerDidLeaveWindowHierarchy` | `ON\_DESTROY` | `CREATED` → `DESTROYED` |

### Web

Due to limitations of the Wasm target, lifecycles:

- Skip the `CREATED` state, as the application is always attached to the page.
- Never reach the `DESTROYED` state, as web pages are usually terminated only when the user closes the tab.

| Native event | Lifecycle event | Lifecycle state change |
| --- | --- | --- |
| `visibilitychange` (becomes visible) | `ON\_START` | `CREATED` → `STARTED` |
| `focus` | `ON\_RESUME` | `STARTED` → `RESUMED` |
| `blur` | `ON\_PAUSE` | `RESUMED` → `STARTED` |
| `visibilitychange` (stops being visible) | `ON\_STOP` | `STARTED` → `CREATED` |

### Desktop

| Swing listener callbacks | Lifecycle event | Lifecycle state change |
| --- | --- | --- |
| `windowIconified` | `ON\_STOP` | `STARTED` → `CREATED` |
| `windowDeiconified` | `ON\_START` | `CREATED` → `STARTED` |
| `windowLostFocus` | `ON\_PAUSE` | `RESUMED` → `STARTED` |
| `windowGainedFocus` | `ON\_RESUME` | `STARTED` → `RESUMED` |
| `dispose` | `ON\_DESTROY` | `CREATED` → `DESTROYED` |

02 September 2025

Manage local resource environment

Multiplatform ViewModel