---
"stx-kafka": patch
---

Closing a `KafkaSubscriber` now ends its `records()` and `process()`, a rebalance drops the revoked partitions' unhandled records and no longer commits offsets for them, and the subscriber, publisher and admin close at most once.
