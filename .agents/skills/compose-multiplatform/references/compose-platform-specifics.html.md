<!-- Generated from https://kotlinlang.org/docs/multiplatform/compose-platform-specifics.html (v) on 2026-08-28. Do not edit; re-run fetch_docs.py. -->

# Default UI behavior on different platforms

Compose Multiplatform aims to help you produce apps which behave as similarly as possible on different platforms. On this page, you can learn about unavoidable differences or temporary compromises which you should expect when writing shared UI code for different platforms with Compose Multiplatform.

## Project structure

Regardless of the platform you're targeting, each of them needs a dedicated entry point:

- For Android, that's the `Activity`, whose job is to show the main composable from common code.
- For an iOS app, that's the `@main` class or structure that initializes the app.
- For a JVM app, that's the `main()` function that starts the application which launches the main common composable.
- For a Kotlin/JS or Kotlin/Wasm app, that's the `main()` function that attaches the main common code composable to the web page.

Certain platform-specific APIs necessary for your app may not have multiplatform support, and you will have to implement calling these APIs in platform-specific source sets. Before doing so, check out [klibs.io](https://klibs.io/), a JetBrains project which aims to comprehensively catalog all available Kotlin Multiplatform libraries. There are already libraries available for network code, databases, coroutines, and much more.

## Input methods

### Software keyboards

Each platform may handle software keyboards slightly differently, including the way a keyboard appears when a text field becomes active.

Compose Multiplatform adopts the [Compose window insets approach](https://developer.android.com/develop/ui/compose/system/insets) and imitates it on iOS to account for [safe areas](https://developer.apple.com/documentation/UIKit/positioning-content-relative-to-the-safe-area). Depending on your implementation, the software keyboard may be positioned a little differently on iOS. Make sure to check that the keyboard does not cover important UI elements on both platforms.

### Touch and mouse support

The current desktop implementation interprets all pointer manipulation as mouse gestures and, therefore, does not support multitouch gestures. For example, the common pinch-to-zoom gesture cannot be implemented with Compose Multiplatform for desktop as it requires processing two touches at once.

## UI behavior and appearance

### Platform-specific functionality

Some common UI elements are not covered by Compose Multiplatform and cannot be customized using the framework. Therefore, you should expect them to look different on different platforms.

Native pop-up views are an example of this: when you select text in a Compose Multiplatform text field, default suggested actions like Copy or Translate are specific to the platform the app is running on.

### Scroll physics

For Android and iOS, the feel of scrolling is aligned with the platform. For desktop, scrolling support is limited to the mouse wheel (as mentioned in Touch and mouse support).

### Interop views

If you would like to embed native views within common composables, or vice versa, you'll need to familiarize yourself with platform-specific mechanisms supported by Compose Multiplatform.

For iOS, there are separate guides for interop code with [SwiftUI](compose-swiftui-integration.html) and [UIKit](compose-uikit-integration.html).

For desktop, Compose Multiplatform supports [Swing interoperability](compose-desktop-swing-interoperability.html).

### Back gesture

Android devices have back gesture support by default, and every screen reacts to the Back button in some fashion.

On iOS, there is no back gesture by default, although developers are encouraged to implement similar functionality to meet user experience expectations. Compose Multiplatform for iOS supports back gestures by default to mimic Android functionality.

On desktop, Compose Multiplatform uses the Esc key as the default back trigger.

For details, see the [Back gesture](compose-navigation.html#back-gesture) section.

### Text

With text, Compose Multiplatform doesn't guarantee pixel-perfect correspondence between different platforms:

- If you don't set a font explicitly, each system assigns a different default font to your text.
- Even for the same font, letter aliasing mechanisms specific to each platform can lead to noticeable differences.

This doesn't have a significant impact on user experience. On the contrary, the default fonts appear as expected on each platform. However, pixel differences may interfere with screenshot testing, for example.

## Developer experience

### Previews

Previews are layout presentations of composables annotated with `@Preview` that can be rendered alongside the shared UI code in IntelliJ IDEA and Android Studio.

Previews require a specific project configuration with explicit dependencies. See [Compose UI previews](compose-previews.html) to learn how to enable previews in your project.

### Hot reload

Hot reload refers to the app reflecting code changes on the fly without requiring additional input. In Compose Multiplatform, hot reload functionality is available only for JVM (desktop) targets. However, you can use it to quickly troubleshoot issues before switching to your intended platforms for fine-tuning.

To learn more, see our [Compose Hot Reload](compose-hot-reload.html) article.

## What's next

Read more about Compose Multiplatform implementation for the following components:

- [Resources](compose-multiplatform-resources.html)
- [Lifecycle](compose-lifecycle.html)
- [Multiplatform ViewModel](compose-viewmodel.html)
- [Navigation and routing](compose-navigation-routing.html)

16 January 2026

How multiplatform Jetpack libraries are packaged

Layout basics