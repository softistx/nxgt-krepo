---
"stx-kafka": patch
---

No change to the API or behaviour: `KafkaSubscriber`'s poll loop, commit bookkeeping and per-partition processing moved into files of their own.
