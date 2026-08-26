<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/product-types/native-app/ (docs 0.12) on 2026-08-26. Do not edit by hand; run sync_docs.py to refresh. -->

# ! Font Awesome Free 7.1.0 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license/free (Icons: CC BY 4.0, Fonts: SIL OFL 1.1, Code: MIT License) Copyright 2025 Fonticons, Inc. Kotlin/Native application

Native applications are applications that run on the host operating system. There are several product types for the supported target platforms:

- `linux/app` - a native Linux application
- `macos/app` - a native macOS application
- `windows/app` - a native Windows application

> **Using IntelliJ IDEA?**

Make sure to install the [- Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.             Kotlin Multiplatform plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform) to get proper support for Kotlin/Native.

## Module layout

Here is an overview of the module layout for a Kotlin/Native application:

```
my-module/
├─ src/
│  ├─ main.kt #(1)!
│  ╰─ Util.kt
├─ test/
│  ╰─ UtilTest.kt
╰─ module.yaml
```

1. This is the conventional entry point location (contains a top-level `fun main()`). See Entry point below.

## Entry point

By default, the entry point of a Kotlin native application is expected to be a top-level `main` function in a `main.kt` file (case-insensitive) in the `src` folder.

If you don't want to follow this convention, you can specifty the fully qualified name of the entry point function explicitly in the module settings:

```yaml
product: linux/app

settings:
  native:
    entryPoint: org.example.myapp.myMainFun # (1)!
```

1. The fully qualified name of the entry point function.

The entry point function must either have **no parameters or one `Array<String>` parameter** (representing the command line arguments).

## Packaging

You can use the `build` command to compile and link a native executable for your application (a `.exe` on Windows, or `.kexe` otherwise).

There is no extra packaging facilities at the moment, and the `package` command is not supported for these native product types.