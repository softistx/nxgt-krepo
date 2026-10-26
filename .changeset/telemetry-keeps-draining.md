---
"stx-telemetry": patch
---

The export pipeline now survives an exporter that cancels itself and reports a backlog dropped at the drain timeout, `withAttributes` falls back to the installed telemetry, `FileExporter` keeps writing after a failed roll, and `MongoExporter` updates its TTL index in place (`collMod`) and drops it when retention is null or under a second (`collMod` needs the `collMod` privilege, which `readWrite` lacks, so without it the index is dropped and created again; a null retention costs one `listIndexes` at the first batch), a retention step that fails no longer stops the batch from being inserted, and a `FileExporter` whose reopen failed after a roll retries it on the next write.
