---
"stx-jpa": minor
---

BREAKING: `findPage` throws `IllegalArgumentException`, naming the attribute, when its query fetches a collection. `limit` and `offset` cut the joined rows, so such a page truncated owners and collections and reported `hasNextPage = false`. Page the owners and load their collections separately, or project.
