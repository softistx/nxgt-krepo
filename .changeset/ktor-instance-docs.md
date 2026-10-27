---
"stx-ktor": patch
"stx-redis": patch
"stx-mongo": patch
"stx-jpa": patch
"stx-storage": patch
"stx-amqp": patch
"stx-telemetry": patch
---

Documentation only: a provided `instance` is not closed by the plugin, but Ktor's DI still closes it at application stop. The KDoc and the `stx-ktor` README now say when that bites and how to replace `onShutdown` in `install(DI)`.
