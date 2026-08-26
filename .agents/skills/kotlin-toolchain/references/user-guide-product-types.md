<!-- Generated from https://kotlin-toolchain.org/0.12/user-guide/product-types/ (docs 0.12) on 2026-08-26. Do not edit by hand; run sync_docs.py to refresh. -->

# Product types

Each module has a product type defined by the `product` field in `module.yaml`, indicating what is created when building this module.

This section contains subsections describing the specific aspects of each product type. Here is the list of supported product types:

| Product type(s) | Description |
| --- | --- |
| [`jvm/lib`](jvm-lib/) | A JVM library |
| [`jvm/app`](jvm-app/) | A JVM console or desktop application |
| [`kmp/lib`](kmp-lib/) | A Kotlin Multiplatform library |
| [`windows/app`](native-app/) | A Kotlin/Native mingw-w64 application |
| [`linux/app`](native-app/) | A Kotlin/Native Linux application |
| [`macos/app`](native-app/) | A Kotlin/Native macOS application |
| [`android/app`](android-app/) | An Android application |
| [`ios/app`](ios-app/) | An iOS application |
| [`js/app`](js-app/) | A JavaScript application using the Kotlin/JS technology |
| [`wasm-js/app`](wasm-js-app/) | A WebAssembly application with browser APIs |
| [`wasm-wasi/app`](wasm-wasi-app/) | A WebAssembly application with WASI APIs |
| [`jvm/amper-plugin`](../plugins/topics/structure/) | A [Kotlin Toolchain plugin](../plugins/overview/), to extend the Kotlin Toolchain build with custom functionality |