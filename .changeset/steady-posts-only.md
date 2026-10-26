---
"stx-graphix": patch
---

A GraphQL mutation sent over GET is now refused with HTTP 405 and `Allow: POST` in both the Ktor and Spring integrations, a graphql-ws operation that finishes no longer drops a new subscription reusing its id, `shutdown()` is safe against concurrent add and remove, and the Spring integration's router, sandbox and graphql-ws beans can each be replaced by a bean of the same name.
