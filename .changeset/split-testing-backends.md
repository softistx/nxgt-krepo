---
"stx-testing": patch
---

Split `Backends.kt` into one file per backend (`MongoContainer.kt`, `PostgresEndpoint.kt`, `Db2Endpoint.kt`, ...) with the three-variable environment override of Postgres, MySQL and DB2 read by one shared helper. No change to the public API or to any environment variable.
