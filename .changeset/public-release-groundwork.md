---
"nxgt-krepo": minor
---

**Breaking: the Maven group is now `io.github.softistx`, not `com.softistx`.** Update every
coordinate — `io.github.softistx:stx-jpa:…`. The Kotlin package prefix is unchanged: imports stay
`com.softistx.*`. Nothing had been published under the old group, so no released artifact moves.

Every artifact now publishes a **sources jar**, and its POM carries the project URL, SCM, Apache-2.0
and a developer — so an IDE shows the source, and the metadata Maven Central requires is already
there.

The libraries are released under **Apache-2.0**, and published to **GitHub Packages** at
`https://maven.pkg.github.com/softistx/nxgt-krepo`. See `docs/consuming.md` for the repository block
and the token GitHub's Maven registry requires even for a public read.
