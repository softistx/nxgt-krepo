<!-- Generated from https://kotlinlang.org/docs/multiplatform/compose-desktop-components.html (v) on 2026-08-28. Do not edit; re-run fetch_docs.py. -->

# Desktop-only API

You can use Compose Multiplatform to create macOS, Linux, and Windows desktop applications. This page gives a short overview of the desktop-specific components and events. Each section includes a link to a detailed tutorial.

## Components

- [Windows and dialogs](compose-desktop-top-level-windows-management.html)
- [Context menus](compose-desktop-context-menus.html)
- [Tray and notifications](compose-desktop-tray.html)
- [Menu bar](compose-desktop-menu-bar.html)
- [Scrollbars](compose-desktop-scrollbars.html)
- [Tooltips](compose-desktop-tooltips.html)

## Events

- [Mouse events](compose-desktop-mouse-events.html)
- [Keyboard events](compose-desktop-keyboard.html)
- Tabbing navigation

### Tabbing navigation between components

You can set up navigation between components with the Tab keyboard shortcut for the next component and ⇧ + Tab for the previous one.

By default, the tabbed navigation allows you to move between focusable components in the order of their appearance. Focusable components include `TextField`, `OutlinedTextField`, and `BasicTextField` composables, as well as components that use `Modifier.clickable`, such as `Button`, `IconButton`, and `MenuItem`.

For example, here's a window where users can navigate between five text fields using standard shortcuts:

```kotlin
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(
        state = WindowState(size = DpSize(350.dp, 500.dp)),
        onCloseRequest = ::exitApplication
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(50.dp)
            ) {
                for (x in 1..5) {
                    val text = remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = text.value,
                        singleLine = true,
                        onValueChange = { text.value = it }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
```

You can also make a non-focusable component focusable, customize the order of tabbing navigation, and put components into focus.

For more information, see the [Tabbing navigation and keyboard focus](https://github.com/JetBrains/compose-multiplatform/tree/master/tutorials/Tab_Navigation) tutorial.

## What's next

- Learn how to [create unit tests for your Compose Multiplatform desktop project](compose-desktop-ui-testing.html).
- Learn how to [create native distributions, installers, and packages for desktop platforms](compose-native-distribution.html).
- Set up [interoperability with Swing and migrate your Swing applications to Compose Multiplatform](compose-desktop-swing-interoperability.html).
- Learn about [accessibility support on different platforms](compose-desktop-accessibility.html).

18 August 2026

Android-only components

Top-level windows management