# Troubleshooting

## Animations differ between machines

**Why.** A `StxTheme` given neither `motionScheme` nor `motion` follows the OS "Reduce motion" setting: on Android
(animator scale 0) and iOS (`UIAccessibilityIsReduceMotionEnabled`) every animation then snaps. A machine with the setting
on shows no animation and a machine with it off shows the springs. Desktop does not read the OS: it follows only the
`-Dstx.material.reduceMotion` system property, so a desktop difference means the property differs between the runs.

**Fix.** Make the choice explicit. Passing either parameter stops the OS read:

```kotlin
StxTheme(motionScheme = MotionScheme.expressive()) { App() }   // always animated
StxTheme(motion = StxMotion(enabled = false)) { App() }        // this library's motion off
```

or pin the desktop property, `-Dstx.material.reduceMotion=false`, in the run configuration or the test task. A test suite
that renders the default theme should pin it the way `test@jvm/io/kotest/provided/ProjectConfig.kt` does.
