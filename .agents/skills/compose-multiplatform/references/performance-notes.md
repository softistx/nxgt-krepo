# Compose Multiplatform notes for `jetpack-compose-performance`

Hand-written, not fetched: `jetpack-compose-performance` stays byte-identical to Google's catalogue, so what differs on Compose Multiplatform is recorded here. `libs/ui/stx-material` runs on Compose Multiplatform 1.12.0 for
jvm, android and iOS; the skill was written for Android. The common runtime, foundation, ui and animation
modules are the same code, so categories 1, 2, 4 (except where noted) and 7 apply unchanged. Checked
against the 1.12.0 jars: `Modifier.onVisibilityChanged`, `Modifier.animateBounds`,
`Modifier.sharedBounds`/`sharedElement`, `SideEffect(keys)` and `derivedStateOf` all exist in common.

What does not apply, or changes:

- **Foundational prerequisites** (release build, R8, baseline profiles) are Android-only. On iOS and
  desktop there is no ART; measure a release build of the app target instead of a debug one.
- **`computedStateOf` does not exist in 1.12.0** (the skill says Compose 1.13+). Use
  `remember { derivedStateOf { … } }` until the version moves.
- **`LocalContext`, `BroadcastReceiver`, `Handler.postDelayed`** are Android classes; there is no common
  equivalent. Resolve other `CompositionLocal`s once, outside a lazy item, as the skill says.
- **`painterResource(R.drawable…)`** becomes the Compose Multiplatform resources library
  (`painterResource(Res.drawable…)`); see the `compose-multiplatform` skill. `AsyncImage` is Coil 3 here.
- **`RuntimeShader` / AGSL** is Android 13+ only. Desktop and iOS use Skia's `RuntimeEffect`, with a
  different API; do not copy the AGSL examples.
- **`collectAsStateWithLifecycle`** comes from `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`
  on Compose Multiplatform (see `compose-multiplatform`'s `compose-lifecycle` page); a module without that
  dependency collects with `collectAsState()`.
- **Tooling**: Layout Inspector, Perfetto/system tracing and Macrobenchmark are Android. On desktop use a
  profiler on the JVM; the recomposition-count and `Modifier.drawWithCache` advice needs no tooling.
- stx-material specifics: interaction-state animation (press, hover, disabled) belongs in a `Style`'s
  `pressed { animate { … } }` blocks, not in `animate*AsState` at the call site (see `stx-material-components`).
