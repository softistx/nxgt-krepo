---
"stx-spring-boot": minor
"stx-mongo": minor
"stx-common": minor
---

Keyset paging no longer drops documents whose sort field is null, missing or a dotted path, a page size is capped at 1000 (`?size=` is coerced down to it), the Mongo audit trail records the signed-in author, writes entries in save order and refuses a duplicate version, and an upstream's unlisted status code such as 522 no longer throws.
