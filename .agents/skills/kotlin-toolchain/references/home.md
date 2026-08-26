<!-- Generated from https://kotlin-toolchain.org/0.12/ (v0.12) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# The Kotlin Toolchain

A unified entry point into Kotlin. Build JVM, Android, iOS, multiplatform, and server-side applications with a simple declarative configuration.

### Command Line

via SDKMAN

```
sdk install kotlintoolchain
```

or via installer script

macOS /

Linux

Windows

```bash
curl -fsSL https://kotl.in/install.sh | sh
```

```
powershell -ExecutionPolicy ByPass -c "irm 'https://kotl.in/install.ps1' | iex"
```

[CLI documentation](cli/)

### - Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.                    IntelliJ IDEA

Install the [Kotlin Toolchain plugin](https://plugins.jetbrains.com/plugin/31850-kotlin-toolchain/) in IntelliJ IDEA 2026.1.2+

**File → New → Project → Kotlin**

[Get started](getting-started/) [User Guide](user-guide/) [Examples](https://github.com/JetBrains/kotlin-toolchain/tree/release/0.12/examples)

## Minimal Configuration

JVM Application

Compose Multiplatform

module.yaml

```yaml
product: jvm/app
```

That's it. Toolchains, test framework, and everything you need — configured automatically.

iOS

Android

Desktop

ios-app/module.yaml

```yaml
product: ios/app

dependencies:
  - //shared

settings:
  compose: enabled
```

android-app/module.yaml

```yaml
product: android/app

dependencies:
  - //shared

settings:
  compose: enabled
```

desktop-app/module.yaml

```yaml
product: jvm/app

dependencies:
  - //shared

settings:
  compose: enabled
```

shared/module.yaml

```yaml
# Produce a shared library for the JVM, Android, and iOS platforms:
product:
  type: kmp/lib
  platforms: [jvm, android, iosArm64, iosSimulatorArm64]

# Shared Compose dependencies:
dependencies:
  - $compose.foundation: exported
  - $compose.material3: exported

# Android-only dependencies
dependencies@android:
  # Android-specific integration with Compose
  - androidx.activity:activity-compose:1.13.0: exported
  - androidx.appcompat:appcompat:1.7.1: exported

settings:
  # Enable Kotlin serialization
  kotlin:
    serialization: json

  # Enable the Compose Multiplatform framework
  compose: enabled
```

Shared UI across Android, iOS, and desktop with a single codebase.

[See more examples](https://github.com/JetBrains/kotlin-toolchain/tree/release/0.12/examples)

The Kotlin Toolchain is [Alpha](https://kotlinlang.org/docs/components-stability.html#stability-levels-explained). We'd love your feedback!

[- Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.                Report an issue](https://youtrack.jetbrains.com/newIssue?project=KTC) [Join Slack](https://kotlinlang.slack.com/archives/C062WG3A7T8)

Android ·  iOS ·  Desktop ·  Server ·  - Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.             Multiplatform ·  - Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.                    Compose