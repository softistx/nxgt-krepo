<!-- Generated from https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme (v) on 2026-10-26. Do not edit; re-run fetch_docs.py. -->

- [Android Developers](https://developer.android.com/)
- [Develop](https://developer.android.com/develop)
- [API reference](https://developer.android.com/reference)

# MotionScheme

Artifact:

androidx.compose.material3:material3

View Source

Added in

1.5.0-beta02

Common/All

Android/JVM

- Cmn platform-Common/All 

```
interface MotionScheme
```

A motion scheme provides all the `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`s for a `[MaterialTheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialTheme)`.

Motion schemes are designed to create a harmonious motion for components in the app.

There are two built-in schemes, a `[standard](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#standard())` and an `[expressive](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#expressive())`, that can be used as-is or customized.

You can customize the motion scheme for all components in the `[MaterialTheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialTheme)`.

## Summary

| Public companion functions |   |   |
| --- | --- | --- |
| `[MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)` | `[expressive](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#expressive())()` Returns an expressive Material motion scheme. | Cmn platform-Common/All |
| `[MotionScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)` | `[standard](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#standard())()` Returns a standard Material motion scheme. | Cmn platform-Common/All |

| Public functions |   |   |
| --- | --- | --- |
| `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)<T>` | `<T : [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?> [defaultEffectsSpec](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#defaultEffectsSpec())()` A default effects motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`. | Cmn platform-Common/All |
| `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)<T>` | `<T : [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?> [defaultSpatialSpec](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#defaultSpatialSpec())()` A default spatial motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`. | Cmn platform-Common/All |
| `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)<T>` | `<T : [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?> [fastEffectsSpec](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#fastEffectsSpec())()` A fast effects motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`. | Cmn platform-Common/All |
| `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)<T>` | `<T : [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?> [fastSpatialSpec](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#fastSpatialSpec())()` A fast spatial motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`. | Cmn platform-Common/All |
| `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)<T>` | `<T : [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?> [slowEffectsSpec](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#slowEffectsSpec())()` A slow effects motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`. | Cmn platform-Common/All |
| `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)<T>` | `<T : [Any](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-any/index.html)?> [slowSpatialSpec](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#slowSpatialSpec())()` A slow spatial motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`. | Cmn platform-Common/All |

## Public companion functions platform-Common/All

### expressive

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun expressive(): MotionScheme
```

Returns an expressive Material motion scheme.

The expressive scheme is Material's recommended motion scheme for prominent UI elements and hero interactions. It provides a visually engaging motion feel.

### standard

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun standard(): MotionScheme
```

Returns a standard Material motion scheme.

The standard scheme is Material's basic motion scheme for utilitarian UI elements and recurring interactions. It provides a linear motion feel.

## Public functions platform-Common/All

### defaultEffectsSpec

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun <T : Any?> defaultEffectsSpec(): FiniteAnimationSpec<T>
```

A default effects motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`.

This motion spec is designed to be applied to animations that do not change the shape or bounds of the component. For example, color animation.

`[T](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#defaultEffectsSpec())` is the generic data type that will be animated by the system, as long as the appropriate `[TwoWayConverter](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/TwoWayConverter)` for converting the data to and from an `[AnimationVector](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/AnimationVector)` is supplied.

### defaultSpatialSpec

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun <T : Any?> defaultSpatialSpec(): FiniteAnimationSpec<T>
```

A default spatial motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`.

This motion spec is designed to be applied to animations that may change the shape or bounds of the component. For color or alpha animations use the `effects` equivalent which ensures a "non-spatial" motion.

`[T](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#defaultSpatialSpec())` is the generic data type that will be animated by the system, as long as the appropriate `[TwoWayConverter](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/TwoWayConverter)` for converting the data to and from an `[AnimationVector](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/AnimationVector)` is supplied.

### fastEffectsSpec

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun <T : Any?> fastEffectsSpec(): FiniteAnimationSpec<T>
```

A fast effects motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`.

This motion spec is designed to be applied to animations that do not change the shape or bounds of the component. For example, color animation.

`[T](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#fastEffectsSpec())` is the generic data type that will be animated by the system, as long as the appropriate `[TwoWayConverter](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/TwoWayConverter)` for converting the data to and from an `[AnimationVector](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/AnimationVector)` is supplied.

### fastSpatialSpec

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun <T : Any?> fastSpatialSpec(): FiniteAnimationSpec<T>
```

A fast spatial motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`.

This motion spec is designed to be applied to animations that may change the shape or bounds of the component. For color or alpha animations use the `effects` equivalent which ensures a "non-spatial" motion.

`[T](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#fastSpatialSpec())` is the generic data type that will be animated by the system, as long as the appropriate `[TwoWayConverter](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/TwoWayConverter)` for converting the data to and from an `[AnimationVector](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/AnimationVector)` is supplied.

### slowEffectsSpec

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun <T : Any?> slowEffectsSpec(): FiniteAnimationSpec<T>
```

A slow effects motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`.

This motion spec is designed to be applied to animations that do not change the shape or bounds of the component. For example, color animation.

`[T](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#slowEffectsSpec())` is the generic data type that will be animated by the system, as long as the appropriate `[TwoWayConverter](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/TwoWayConverter)` for converting the data to and from an `[AnimationVector](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/AnimationVector)` is supplied.

### slowSpatialSpec

Cmn

platform-Common/All

Added in

1.5.0-beta02

```
fun <T : Any?> slowSpatialSpec(): FiniteAnimationSpec<T>
```

A slow spatial motion `[FiniteAnimationSpec](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/FiniteAnimationSpec)`.

This motion spec is designed to be applied to animations that may change the shape or bounds of the component. For color or alpha animations use the `effects` equivalent which ensures a "non-spatial" motion.

`[T](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme#slowSpatialSpec())` is the generic data type that will be animated by the system, as long as the appropriate `[TwoWayConverter](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/TwoWayConverter)` for converting the data to and from an `[AnimationVector](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/AnimationVector)` is supplied.