<!-- Generated from https://foso.github.io/Ktorfit/knownissues/ (v) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# Known Issues

## KMP project with single target

- Unresolved reference for API class

When you have a KMP project with a single target, IntelliJ will find the generated “create” extension function (e.g. **ktorfit.createExampleApi()**) in your common module, but the compilation will fail because of an “Unresolved reference” error. In that case, you have to use  **ktorfit.create<ExampleApi>()**  to make it work, even though it’s already deprecated.

Kotlin handles the compilation of a KMP project with a single target differently than with multiple targets.

See:

- [https://youtrack.jetbrains.com/issue/KT-59129](https://youtrack.jetbrains.com/issue/KT-59129)
- [https://youtrack.jetbrains.com/issue/KT-52664/Multiplatform-projects-with-a-single-target](https://youtrack.jetbrains.com/issue/KT-52664/Multiplatform-projects-with-a-single-target)