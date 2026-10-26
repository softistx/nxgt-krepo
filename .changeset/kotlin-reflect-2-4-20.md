---
"stx-amqp": patch
"stx-graphix": patch
"stx-i18n": patch
"stx-jpa": patch
"stx-kafka": patch
"stx-migrations": patch
"stx-mongo": patch
"stx-redis": patch
"stx-spring-boot": patch
"stx-storage": patch
"stx-telemetry": patch
"stx-workflow": patch
---

kotlin-reflect moves to 2.4.20, matching the Kotlin stdlib the toolchain ships, and classgraph (classpath scanning in stx-jpa) moves from 4.8 to 6.0.0, a major release of that dependency that needs JDK 25, so a consumer that calls classgraph 4 APIs directly has to move too.
