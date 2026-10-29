---
"stx-material": minor
---

`relativeTime`, `seenByLabel`, `RelativeTime` and `SeenBy` follow the catalogue's language, and `StxStrings` gains the validation messages (`required`, `invalidEmail`, `minLengthRule`, …) to pass to the form rules.

`StxStrings` gains 10 validation keys, so code constructing `StxStrings` directly (not via `copy()`) must supply them.
