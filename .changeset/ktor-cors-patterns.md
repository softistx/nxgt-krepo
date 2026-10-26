---
"stx-ktor": patch
"stx-testing": patch
"stx-telemetry": patch
---

`cors()` now honours `originPatterns` (a glob such as `https://*.example.com`) and refuses an origin written without its `scheme://`; `ContainerService.describe()` resolves the service before saying where it came from; the `stx.telemetry.version` KDoc no longer promises a jar-manifest fallback.
