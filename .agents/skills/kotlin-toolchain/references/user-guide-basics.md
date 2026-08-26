<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/basics/ (docs 0.12) on 2026-08-26. Do not edit by hand; run sync_docs.py to refresh. -->

# Basic concepts

If you have never used the Kotlin Toolchain before, this is the best place to start. In this section, we will learn about the basic entities in the Kotlin Toolchain, the general project layout, and how configuration files look.

## Project and modules

A Kotlin **project** is defined by a `project.yaml` file. This file contains the list of modules and the project-wide configuration. The folder with the `project.yaml` file is the **project root**. Modules can only be located under the project root (at any depth). If there is only one module in the project, the `project.yaml` file is not required.

A Kotlin **module** is a directory with a `module.yaml` configuration file, and optionally sources and resources. A *module configuration file* describes *what* to produce: e.g. a reusable library or a platform-specific application. Each module describes a single product. Several modules can't share the same sources or resources, but they can depend on each other. *How* to produce the desired product, that is, the build rules, is the responsibility of the Kotlin Toolchain build engine.

## Project layout

### Single-module project

A single-module Kotlin project doesn't need a `project.yaml` file. Just create a single valid module, and it is also a valid project1:

Single-module project layout

```
my-project/ #(1)!
├─ src/
│  ├─ main.kt
├─ test/
│  ╰─ MainTest.kt
╰─ module.yaml
```

1. This is the project root but also the root of the only module in the project.

See the Module layout section for more details about the module structure itself.

### Multi-module project

If there are multiple modules, the `project.yaml` file specifies the list of modules:

Multi-module project layout

```
├─ app/
│  ├─ src/
│  │  ├─ main.kt
│  │  ╰─ ...
│  ╰─ module.yaml
├─ libs/ #(1)!
│  ├─ lib1/
│  │  ├─ src/
│  │  │  ╰─ myLib1.kt
│  │  ╰─ module.yaml
│  ╰─ lib2/
│     ├─ src/
│     │  ╰─ myLib2.kt
│     ╰─ module.yaml
╰─ project.yaml
```

1. This hierarchy is arbitrary, it can be organized however you like. The structure is understood by the Kotlin Toolchain based on the list of module paths in `project.yaml` — there is no convention for multi-module projects.

project.yaml

```yaml
modules:
  - app
  - libs/lib1 #(1)!
  - libs/lib2
```

app/module.yaml

```yaml
product: jvm/app

dependencies:
  - //libs/lib1
  - //libs/lib2
```

1. It is also possible to use globs to list multiple modules at once (e.g., `libs/\*`), although we encourage listing them explicitly. See details in the [project file reference](../../reference/project/#modules).

See the Module layout section for more details about what goes inside each module directory.

**Multi-module project with root module**

It is also possible to have a root module even if there are multiple modules in the project, although this is generally discouraged.

```
├─ lib/
│  ├─ src/
│  │  ╰─ util.kt
│  ╰─ module.yaml
├─ src/  # src of the root module
│  ├─ main.kt
│  ╰─ ...
├─ module.yaml  # the module file of the root module
╰─ project.yaml
```

project.yaml

```yaml
modules:  # The root module is included implicitly
  - lib
```

## Module layout

Here are typical module structures at a glance:

- Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

JVM

- Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

Kotlin Multiplatform

Android app

iOS app

```
my-module/
├─ resources/ # (1)!
│  ╰─ logback.xml # (2)!
├─ src/
│  ├─ main.kt
│  ╰─ Util.java # (3)!
├─ test/
│  ╰─ MainTest.java # (4)!
│  ╰─ UtilTest.kt
├─ testResources/
│  ╰─ logback-test.xml # (5)!
╰─ module.yaml
```

1. Resources placed here are copied into the resulting jar.
2. This is just an example resource and can be omitted.
3. You can mix Kotlin and Java source files in a single module, all in the `src` folder.
4. You can test Java code with Kotlin tests or Kotlin code with Java tests.
5. This is just an example resource and can be omitted.

> **Maven compatibility layout for JVM-only modules**

If you're migrating from Maven, you can also configure the [Maven-like layout](../advanced/maven-like-layout/)

```
my-module/
├─ resources/       # common resources, used in all targets
├─ resources@ios/   # resources that are only available to the iOS code
├─ resources@jvm/   # resources that are only available to the JVM code
├─ src/             # common code, compiled for all targets
│  ├─ main.kt
│  ╰─ util.kt # (1)!
├─ src@native/      # code to be compiled for all native targets
├─ src@apple/       # code to be compiled for all Apple targets
├─ src@ios/         # code to be compiled only for iOS targets
│  ╰─ util.kt # (2)!
├─ src@jvm/         # code to be compiled only for JVM targets
│  ├─ util.kt
│  ╰─ MyClass.java # (3)!
├─ test/            # common tests, compiled for all targets
│  ╰─ MainTest.kt
├─ test@ios/        # tests that are only run on iOS simulator
│  ╰─ SomeIosTest.kt
├─ test@jvm/        # tests that are only run on JVM
│  ╰─ SomeJvmTest.kt
├─ testResources/       # common test resources, used in all targets
├─ testResources@ios/   # test resources that are only available to the iOS code
├─ testResources@jvm/   # test resources that are only available to the JVM code
╰─ module.yaml
```

1. This file may define `expect` declarations to be implemented differently on different platforms.
2. This file defines the `actual` implementations corresponding to the `expect` declarations from `src`.
3. It's ok to have Java sources in JVM-only source directories.

> **Read more in the dedicated [Multiplatform modules](../multiplatform/) section.**

```
my-android-app/
├─ assets/ # (1)!
├─ jniLibs/ # (2)!
│  ╰─ arm64-v8a/
│     ╰─ libfoo.so
├─ res/ # (3)!
│  ├─ drawable/
│  │  ╰─ graphic.png
│  ├─ layout/
│  │  ├─ main.xml
│  │  ╰─ info.xml
│  ╰─ ...
├─ resources/
├─ src/
│  ├─ AndroidManifest.xml # (4)!
│  ╰─ MainActivity.kt # (5)!
├─ test/
│  ╰─ MainTest.kt
├─ module.yaml
╰─ proguard-rules.pro # (6)!
```

1. `assets` and `res` are standard Android resource directories. See the [official Android docs](https://developer.android.com/guide/topics/resources/providing-resources).
2. Pre-compiled native libraries (`.so` files) organized by ABI (e.g. `arm64-v8a`, `x86\_64`). See the [official Android docs](https://developer.android.com/studio/projects/gradle-external-native-builds#jniLibs).
3. `assets` and `res` are standard Android resource directories. See the [official Android docs](https://developer.android.com/guide/topics/resources/providing-resources).
4. The manifest file of your application.
5. An activity (screen) of your application.
6. Optional configuration for R8 code shrinking and obfuscation. See [code shrinking](../product-types/android-app/#code-shrinking).

> **Read more in the dedicated [Android](../product-types/android-app/) section.**

```
my-ios-app/
├─ src/
│  ├─ KotlinCode.kt # (1)!
│  ├─ EntryPoint.swift # (2)!
│  ╰─ Info.plist
├─ module.yaml
╰─ module.xcodeproj # (3)!
```

1. Kotlin code is optional. Everything could come from dependencies.
2. The entrypoint of iOS apps must be a `@main` struct in a Swift file. [Read more](../product-types/ios-app/#entry-point)
3. An Xcode project is required but it can be automatically generated by the Kotlin Toolchain the first time. [Read more](../product-types/ios-app/#xcode-project)

> **Read more in the dedicated [iOS](../product-types/ios-app/) section.**

All sources and resources are optional: **only the `module.yaml` file is required.** For example, your module could get all its code from dependencies and have no `src` folder.

Sources and resources can't be defined as part of multiple modules — they must belong to a single module, which other modules can depend on. This ensures that the IDE always knows how to analyze and refactor the code, as it always has a single well-defined set of settings and dependencies.

## Module file anatomy

A `module.yaml` file has several main sections: `product`, `dependencies` and `settings`.

> **If you are not familiar with YAML, see [our brief YAML primer](../yaml-primer/).**

A module can produce a single product, such as a reusable library or an application. Read more on the supported product types below.

Here are some example module files for different types of modules:

- Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

JVM application

- Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

KMP

library

```yaml
product: jvm/app #(1)!

dependencies:
  - io.ktor:ktor-client-java:2.3.0 #(2)!

settings: #(3)!
  kotlin:
    version: 2.2.21 #(4)!
    allWarningsAsErrors: true #(5)!
```

1. This short form is equivalent to: 

```yaml
 product:
   type: jvm/app
```

 The `jvm/app` product type means that the module produces a [JVM application](../product-types/jvm-app/). Read more about other product types in the [Product types](../product-types/) section.
2. The `dependencies` section contains the list of dependencies for this module. Here `io.ktor:ktor-client-java:2.3.0` are the [Maven coordinates ! Font Awesome Free 7.1.0 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free (Icons: CC BY 4.0, Fonts: SIL OFL 1.1, Code: MIT License) Copyright 2025 Fonticons, Inc.](https://maven.apache.org/pom.html#Maven_Coordinates) of the Ktor client library (with Java engine). Read more about dependencies in general in the [Dependencies](../dependencies/) section.
3. The `settings` section contains the configuration of different toolchains.
4. An example setting: the Kotlin compiler version used for this module.
5. An example setting: a compiler setting to consider warnings as errors and fail the build on any warning.

```yaml
product:
  type: kmp/lib #(1)!
  platforms: [android, iosArm64, iosSimulatorArm64] #(2)!

dependencies:
  - io.ktor:ktor-client-core:2.3.0 #(3)!

dependencies@android:
  - io.ktor:ktor-client-android:2.3.0 #(4)!

dependencies@ios:
  - io.ktor:ktor-client-darwin:2.3.0 #(5)!

settings: #(6)!
  kotlin:
    version: 2.2.21 #(7)!
    allWarningsAsErrors: true #(8)!

settings@ios: #(9)!
  kotlin:
    allWarningsAsErrors: false #(10)!
```

1. The `kmp/lib` product type means that the module produces a [- Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.             Kotlin Multiplatform library](../product-types/kmp-lib/). Read more about other product types in the [Product types](../product-types/) section.
2. The `platforms` list contains the platforms that this module is built for.
3. The `dependencies` section contains the list of common dependencies for this module. Here `io.ktor:ktor-client-core:2.3.0` are the [Maven coordinates ! Font Awesome Free 7.1.0 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free (Icons: CC BY 4.0, Fonts: SIL OFL 1.1, Code: MIT License) Copyright 2025 Fonticons, Inc.](https://maven.apache.org/pom.html#Maven_Coordinates) of the Ktor client core library. Read more about dependencies in general in the [Dependencies](../dependencies/) section. Read more about multiplatform dependencies in the [Multiplatform dependencies](../multiplatform/#multiplatform-dependencies) section.
4. The `dependencies@android` section contains the list of dependencies that are only used when building the module for the Android target. Here the `io.ktor:ktor-client-android:2.3.0` will not be present when building the module for the iOS targets. Read more about dependencies in general in the [Dependencies](../dependencies/) section. Read more about multiplatform dependencies in the [Multiplatform dependencies](../multiplatform/#multiplatform-dependencies) section.
5. The `dependencies@ios` section contains the list of dependencies that are only used when building the module for the iOS target. Here the `io.ktor:ktor-client-darwin:2.3.0` will not be present when building the module for the Android target. Read more about dependencies in general in the [Dependencies](../dependencies/) section. Read more about multiplatform dependencies in the [Multiplatform dependencies](../multiplatform/#multiplatform-dependencies) section.
6. The `settings` section contains the configuration of different toolchains for common code, and also serves as default for platform-specific code..
7. An example setting: the Kotlin compiler version used for this module.
8. An example setting: a compiler setting to consider warnings as errors and fail the build on any warning.
9. The `settings@ios` section contains the configuration of different toolchains for iOS-specific compilation.
10. This setting overrides the one that we set in the `settings` section. Read more about this in the [settings propagation](../multiplatform/#multiplatform-settings) section.

### Product type

The **product type** describes what is created when building the module: a JVM application (`jvm/app`), Android application (`android/app`), Kotlin Multiplatform library (`kmp/lib`), etc. It actually tells us both the target platform and the type of the module at the same time.

All modules generally work the same way, but each product type may add its own set of rules and capabilities. Check out the [Product types](../product-types/) section and subsections to see details about each of them.

### Dependencies

The `dependencies` section contains the list of dependencies for this module. They can be external maven libraries, other modules in the project, and more.

See the [Dependencies](../dependencies/) section for more details.

### Settings

The `settings` section contains toolchains settings. A *toolchain* is an SDK (Kotlin, Java, Android, iOS) or a simpler tool (linter, code generator).

All toolchain settings are specified in dedicated groups in the `settings` section: 

```yaml
settings:
  kotlin:
    languageVersion: 2.3
  android:
    compileSdk: 31
```

Check out the [Reference](../../reference/module/#settings-and-test-settings) page for the full list of supported settings.

See the [Multiplatform modules](../multiplatform/) section for more details about how multiple settings sections interact in multiplatform modules.

### Path notation

All configuration files use a forward slash `/` as a path component separator, on every platform. Backslashes `\` should not be used, even on Windows.

To refer to a file or directory in the project, use `//`-prefixed paths, for example `//libs/utils` or `//LICENSE.txt`. In this notation, paths are resolved from the project root directory, where the `project.yaml` (or the single `module.yaml`) is located.

This works for module dependencies, module templates, and in other places, where a `Path` value is expected, and it is the preferred way of working with paths.

Simple relative paths are also supported, for example `./foo.txt`, `resources/picture.jpg` or `../bar.bin`. Such paths are resolved against **the directory containing the `.yaml` file where the path is specified**.

> **Tip: Prefer `//` over `../`**

It's recommended to use `//` path notation over relative `../` paths in most cases. This way, moving the `yaml` file will not affect the paths within.

1. As long as it is not included in a `project.yaml` higher in the directory tree. ↩