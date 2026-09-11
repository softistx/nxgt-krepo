---
"nxgt-krepo": minor
---

**Breaking: the Maven group is now `io.github.softistx`, not `com.softistx`.** Update every
coordinate — `io.github.softistx:stx-jpa:…`. The Kotlin package prefix is unchanged: imports stay
`com.softistx.*`. Nothing had been published under the old group, so no released artifact moves.

Every artifact now publishes a **sources jar**, and its POM carries the project URL, SCM, Apache-2.0
and a developer — so an IDE shows the source, and the metadata Maven Central requires is already
there.

The libraries are released under **Apache-2.0** and published to **Maven Central**: no repository
block, no credentials, PGP-signed. `docs/consuming.md` has the Gradle, Maven and Kotlin Toolchain
forms.
