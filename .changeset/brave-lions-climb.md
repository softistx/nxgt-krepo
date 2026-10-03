---
---

No source and no `version:` changes: the toolchain wrappers move to 0.13.0, the Koin compiler plugin
used only by the unpublished `server/oauth` moves to 1.2.1, and `stx-material`'s manifest gains a
corrected comment about its Compose pin.

What the toolchain does change, without a release of its own: the next published version of every
family declares `kotlin-stdlib` 2.4.20 (was 2.4.10), and of every Spring family imports
`spring-boot-dependencies` 4.1.1 (was 4.1.0) — both written by the toolchain, measured in the
`mavenLocal` POMs.
