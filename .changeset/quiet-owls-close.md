---
"stx-kafka": patch
---

Closing a `KafkaSubscriber` now ends its `records()` and `process()`, a rebalance drops the revoked partitions' unhandled records and no longer commits offsets for them, the subscriber, publisher and admin close at most once, a revocation no longer reorders the records of the partitions a consumer keeps, and `process` with `Concurrency.PerPartition` returns once the subscriber closes.
