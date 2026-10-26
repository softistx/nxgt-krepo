---
"stx-telemetry": patch
---

The export pipeline now survives an exporter that cancels itself and reports a backlog dropped at the drain timeout, `withAttributes` falls back to the installed telemetry, `FileExporter` keeps writing after a failed roll, and `MongoExporter` updates its TTL index in place (`collMod`) and drops it when retention is null or zero.
