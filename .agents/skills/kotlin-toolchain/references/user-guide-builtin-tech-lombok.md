<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/builtin-tech/lombok/ (docs 0.12) on 2026-08-26. Do not edit by hand; run sync_docs.py to refresh. -->

# - Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.       Lombok

[Project Lombok](https://projectlombok.org/) is a Java library that generates getters, setters, builders, and other boilerplate code from annotations.

The Kotlin Toolchain provides the `settings.lombok` option to configure Lombok conveniently in your project: 

```yaml
settings:
  lombok: enabled
```

When Lombok is enabled, the Kotlin Toolchain adds the `lombok` dependency, the annotation processor for Java, and the Kotlin compiler plugin.

You can also customize the version of the Lombok library using the full form of the configuration: 

```yaml
settings:
  lombok:
    enabled: true
    version: 1.18.42
```