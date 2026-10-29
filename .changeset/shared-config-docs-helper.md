---
"stx-testing": minor
"stx-spring-boot": patch
"stx-graphix": patch
"stx-i18n": patch
"stx-jpa": patch
"stx-migrations": patch
"stx-mongo": patch
"stx-storage": patch
"stx-kafka": patch
"stx-telemetry": patch
"stx-amqp": patch
"stx-workflow": patch
"stx-redis": patch
---

`stx-testing` adds `springConfigurationDocs(resourceOwner)`, which reads a module's Spring configuration metadata and the keys `docs/spring-configuration.md` spells for its groups, so a `ConfigurationDocsTest` is a spec and its owned groups rather than a 70-line copy. The Spring integrations' own specs now use it; nothing they publish changes.
