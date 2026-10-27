---
"stx-spring-boot": minor
---

`parseFilter`, `toSort`, `mongoPage` and the new `mongoFilter(allowed)` / `mongoQuery(allowed)` (for `find` routes; the properties of those names stay the open case) take an optional `allowed: Set<String>`, the fields a client may name in `?filter=` and `?sort=`. A field outside it is refused as `filters.invalid`, for a sort as well as a filter; dotted paths are matched whole. Left null, every field stays reachable, so `?filter=passwordHash:like:a` remains an oracle: set it on any route over a collection with a field that must not leak.

The defaulted `allowed` parameter changes the JVM signatures of `parseFilter`, `toSort` and `mongoPage`, so consumers must recompile (source-compatible).
