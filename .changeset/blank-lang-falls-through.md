---
"stx-i18n": minor
---

A blank `?lang=` now falls through to `Accept-Language` in the Ktor plugin, and the Spring locale resolver is built from the `Messages` bean, so an application's own `Messages` decides the supported and default locales.
