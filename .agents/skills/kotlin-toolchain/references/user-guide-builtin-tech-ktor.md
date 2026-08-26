<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/builtin-tech/ktor/ (v0.12) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# - Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.              Ktor

[Ktor](https://ktor.io/) is a Kotlin framework for building asynchronous server-side and client-side applications.

To enable Ktor support, add the following to the `module.yaml` file: 

```yaml
settings:
  ktor: enabled
```

Setting `ktor: enabled` performs the following actions:

- Applies Ktor BOM
- Contributes Ktor-related entries to a built-in library catalog
- Adds the `io.ktor.development=true` system property when running the app with `kotlin run`

Examples of Ktor projects:

- [ktor-simplest-sample](https://github.com/JetBrains/kotlin-toolchain/tree/release/0.12/examples/ktor-simplest-sample)

You can also customize the version of the Ktor libraries using the full form of the configuration: 

```yaml
settings:
  ktor:
    enabled: true
    version: 3.3.2
```

If you don't want the Ktor BOM to be applied, you can disable it explicitly: 

```yaml
settings:
  ktor:
    enabled: true
    applyBom: false
```