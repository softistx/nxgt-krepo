<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/builtin-tech/kotlinx-rpc/ (v0.12) on 2026-08-26. Do not edit; re-run fetch_docs.py. -->

# xml version="1.0" encoding="UTF-8"? - Copyright 2023-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.   Kotlinx RPC

The [kotlinx.rpc](https://kotlin.github.io/kotlinx-rpc/get-started.html) library allows you to implement Remote Procedure Calls (RPC) more easily by generating boilerplate code behind the scenes for you.

To enable kotlinx.rpc support, add the following to the `module.yaml` file of each client or server module, and each module declaring `@Rpc` services:

```yaml
settings:
  kotlin:
    rpc: enabled
```

This will automatically:

- enable code generation for your `@Rpc` services via the kotlinx.rpc compiler plugin
- add the required `org.jetbrains.kotlinx:kotlinx-rpc-core` runtime dependency (implicitly)
- apply the kotlinx.rpc [BOM (Bill of Materials)](../../dependencies/#using-a-maven-bom) to align the versions of the RPC-related artifacts
- add some useful [library catalog](../../dependencies/#library-catalogs) entries starting with `$kotlin.rpc.`