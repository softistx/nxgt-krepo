# Troubleshooting

## Animations differ between machines

**Why.** A `StxTheme` given no `motion` follows the OS "Reduce motion" setting: on Android
(animator scale 0) and iOS (`UIAccessibilityIsReduceMotionEnabled`) every animation then snaps. A machine with the setting
on shows no animation and a machine with it off shows the springs. Desktop does not read the OS: it follows only the
`-Dstx.material.reduceMotion` system property, so a desktop difference means the property differs between the runs.

**Fix.** Make the choice explicit. Passing `motion` stops the OS read:

```kotlin
StxTheme(motion = StxMotion()) { App() }                       // always animated
StxTheme(motion = StxMotion(enabled = false)) { App() }        // all motion off, M3 included
```

or pin the desktop property, `-Dstx.material.reduceMotion=false`, in the run configuration or the test task. A test suite
that renders the default theme should pin it the way `test@jvm/io/kotest/provided/ProjectConfig.kt` does.
